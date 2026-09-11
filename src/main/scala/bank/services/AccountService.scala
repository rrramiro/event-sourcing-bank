package bank.services

import java.util.UUID

import bank.model.aggregates.{Account, AccountState, AggregateError}
import bank.model.commands._
import bank.model.events.Event
import bank.storage.{EventStore, SnapshotStore}
import cats.data.EitherT
import cats.effect._
import cats.instances.int._
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.apply._
import fs2.concurrent.Topic

class AccountService[F[_]: Concurrent](
  eventStore: EventStore[F],
  eventsTopic: Topic[F, Event],
  snapshotStore: SnapshotStore[F, AccountState],
  snapshotEvery: Int
) {

  type ResultT[T] = EitherT[F, AggregateError, T]

  def load(id: UUID): ResultT[Account] =
    EitherT.right[AggregateError](snapshotStore.load(id)) >>= { snapshot =>
      EitherT.right[AggregateError](eventStore.loadSince(id, snapshot.fold(0)(_.version))) >>= { events =>
        Account.load[ResultT](id)(events, snapshot.map(s => s.state -> s.version))
      }
    }

  def process(command: AccountCommand): ResultT[Account] =
    command match {
      case OpenAccountCommand(clientId) =>
        Account.open[ResultT](UUID.randomUUID(), clientId) >>= storeAndPublishEvents
      case WithdrawAccountCommand(id, amount) =>
        loadProcessStorePublish(id)(Account.withdrawn[ResultT](amount))
      case DepositAccountCommand(id, amount) =>
        loadProcessStorePublish(id)(Account.deposit[ResultT](amount))
    }

  private def loadProcessStorePublish(id: UUID)(f: Account => ResultT[Account]): ResultT[Account] =
    load(id) >>= f >>= storeAndPublishEvents

  // `broadcastThrough(eventsTopic.publish)` would treat each call's (finite) event stream completing
  // as "the publisher is done" and close the shared topic - fine for a single long-lived publisher,
  // wrong here since every command publishes its own short-lived stream. `evalMap(publish1)` just
  // publishes each event without ever signalling topic completion.
  private def storeAndPublishEvents(account: Account): ResultT[Account] =
    EitherT(eventStore.store(account.aggregateId)) *>
      EitherT.right[AggregateError] {
        fs2
          .Stream(account.aggregateId.newEvents: _*)
          .covary[F]
          .evalMap(eventsTopic.publish1)
          .compile
          .drain
          .productR(maybeSnapshot(account))
          .as(account)
      }

  // Every `snapshotEvery` committed versions, cache the fold result so a later `load` can resume
  // from here instead of replaying from event #1. Purely an optimization: correctness never depends
  // on a snapshot existing, since `load` falls back to the full log when there isn't one.
  private def maybeSnapshot(account: Account): F[Unit] = {
    val committedVersion = account.aggregateId.baseVersion + account.aggregateId.newEvents.size
    if (committedVersion % snapshotEvery === 0)
      snapshotStore.save(account.aggregateId.id, committedVersion, account.state)
    else
      Concurrent[F].unit
  }
}

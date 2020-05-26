package bank.services

import java.util.UUID

import bank.model.aggregates.{Account, AggregateError}
import bank.model.commands._
import bank.model.events.Event
import bank.storage.EventStore
import cats.data.EitherT
import cats.effect._
import cats.mtl.implicits._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.apply._
import fs2.concurrent.Topic

class AccountService[F[_]: Sync: Concurrent](
  eventStore: EventStore[F],
  eventsTopic: Topic[F, Event]
) {

  type ResultT[T] = EitherT[F, AggregateError, T]

  def load(id: UUID): ResultT[Account] =
    EitherT.right[AggregateError](eventStore.load(id)) >>= Account.load[ResultT](id)

  def process(command: Command): ResultT[Account] =
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

  private def storeAndPublishEvents(account: Account): ResultT[Account] =
    EitherT.right[AggregateError] {
      eventStore.store(account.aggregateId) *>
        fs2
          .Stream(account.aggregateId.newEvents: _*)
          .covary[F]
          .broadcastTo(eventsTopic.publish)
          .compile
          .drain
          .as(account)
    }
}

package bank.services

import java.util.UUID

import bank.model.aggregates.{Account, AggregateError}
import bank.model.commands._
import bank.model.events.Event
import bank.storage.EventStore
import cats.effect._
import cats.mtl.Raise
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.apply._
import fs2.concurrent.Topic

class AccountService[F[_]: Concurrent](
  eventStore: EventStore[F],
  eventsTopic: Topic[F, Event]
) {

  def load(id: UUID)(implicit G: Raise[F, AggregateError]): F[Account] =
    eventStore.load(id) >>= Account.load[F](id)

  def process(command: Command)(implicit G: Raise[F, AggregateError]): F[Account] =
    command match {
      case OpenAccountCommand(clientId) =>
        Account.open[F](UUID.randomUUID(), clientId) >>= storeAndPublishEvents
      case WithdrawAccountCommand(id, amount) =>
        loadProcessStorePublish(id)(Account.withdrawn[F](amount))
      case DepositAccountCommand(id, amount) =>
        loadProcessStorePublish(id)(Account.deposit[F](amount))
    }

  private def loadProcessStorePublish(
    id: UUID
  )(f: Account => F[Account])(implicit G: Raise[F, AggregateError]): F[Account] =
    load(id) >>= f >>= storeAndPublishEvents

  private def storeAndPublishEvents(account: Account): F[Account] =
    eventStore.store(account.aggregateId) *>
      fs2
        .Stream(account.aggregateId.newEvents: _*)
        .covary[F]
        .broadcastTo(eventsTopic.publish)
        .compile
        .drain
        .as(account)
}

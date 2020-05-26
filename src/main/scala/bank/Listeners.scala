package bank

import bank.model.events._
import bank.model.projection._
import bank.storage._
import cats.effect._
import fs2.Pipe
import fs2.concurrent.Topic

object Listeners {

  def subscribeListeners[F[_]: Concurrent](
    eventsTopic: Topic[F, Event],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F]
  ): fs2.Stream[F, Unit] = {
    val events = eventsTopic.subscribe(10)
    fs2
      .Stream[F, fs2.Stream[F, Unit]](
        events.through(accountsListener(accountsRepository)),
        events.through(transactionsListener(transactionsRepository))
      )
      .parJoin(2)
  }

  def accountsListener[F[_]: Sync](
    accountsRepository: AccountsRepository[F]
  ): Pipe[F, Event, Unit] =
    _.evalMap {
      case event: AccountOpenedEvent =>
        accountsRepository.save(
          AccountProjection(
            event.eventId.aggregateId,
            event.clientId,
            event.balance,
            event.eventId.version
          )
        )
      case event: AccountDepositedEvent =>
        accountsRepository.updateBalance(
          event.eventId.aggregateId,
          event.balance,
          event.eventId.version
        )
      case event: AccountWithdrawnEvent =>
        accountsRepository.updateBalance(
          event.eventId.aggregateId,
          event.balance,
          event.eventId.version
        )
      case _ => Sync[F].unit
    }

  def transactionsListener[F[_]: Sync](
    transactionsRepository: TransactionsRepository[F]
  ): Pipe[F, Event, Unit] =
    _.evalMap {
      case event: AccountDepositedEvent =>
        transactionsRepository.save(
          TransactionProjection(
            event.eventId.aggregateId,
            TransactionType.Deposit,
            event.amount,
            event.eventId.timestamp,
            event.eventId.version
          )
        )
      case event: AccountWithdrawnEvent =>
        transactionsRepository.save(
          TransactionProjection(
            event.eventId.aggregateId,
            TransactionType.Withdrawal,
            event.amount,
            event.eventId.timestamp,
            event.eventId.version
          )
        )
      case _ => Sync[F].unit
    }
}

package bank

import bank.model.events._
import bank.model.projection._
import bank.storage._
import cats.effect._
import fs2.Pipe
import fs2.concurrent.Topic

object Listeners {

  def subscribeListeners[F[_]: Async](
    eventsTopic: Topic[F, Event],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F]
  ): fs2.Stream[F, Unit] = {
      eventsTopic.subscribe(10).through(accountsListener(accountsRepository)) concurrently
        eventsTopic.subscribe(10).through(transactionsListener(transactionsRepository))
  }

  def accountsListener[F[_]: Sync](
    accountsRepository: AccountsRepository[F]
  ): Pipe[F, Event, Unit] =
    _.evalMap {
      case event: AccountOpenedEvent =>
        println("a"*10)
        accountsRepository.save(
          AccountProjection(
            event.eventId.aggregateId,
            event.clientId,
            event.balance,
            event.eventId.version
          )
        )
      case event: AccountDepositedEvent =>
        println("b"*10)
        accountsRepository.updateBalance(
          event.eventId.aggregateId,
          event.balance,
          event.eventId.version
        )
      case event: AccountWithdrawnEvent =>
        println("c"*10)
        accountsRepository.updateBalance(
          event.eventId.aggregateId,
          event.balance,
          event.eventId.version
        )
      case _ =>
        println("d"*10)
        Sync[F].unit
    }

  def transactionsListener[F[_]: Sync](
    transactionsRepository: TransactionsRepository[F]
  ): Pipe[F, Event, Unit] =
    _.evalMap {
      case event: AccountDepositedEvent =>
        println("-"*10)
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
        println("x"*10)
        transactionsRepository.save(
          TransactionProjection(
            event.eventId.aggregateId,
            TransactionType.Withdrawal,
            event.amount,
            event.eventId.timestamp,
            event.eventId.version
          )
        )
      case _ =>
        println("i"*10)
        Sync[F].unit
    }
}

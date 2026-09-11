package bank

import bank.model.events._
import bank.model.projection._
import bank.storage._
import cats.effect._
import cats.syntax.apply._
import fs2.Pipe
import fs2.concurrent.Topic

object Listeners {

  // Registers both subscriptions (via subscribeAwait) before the Resource is acquired, so that
  // any events published once `use` starts running are guaranteed to reach both listeners -
  // unlike `Topic#subscribe`, which only starts capturing events once the returned stream is pulled.
  def subscribeListeners[F[_]: Async](
    eventsTopic: Topic[F, Event],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F]
  ): Resource[F, fs2.Stream[F, Unit]] =
    (eventsTopic.subscribeAwait(10), eventsTopic.subscribeAwait(10)).mapN { (accountEvents, transactionEvents) =>
      fs2
        .Stream[F, fs2.Stream[F, Unit]](
          accountEvents.through(accountsListener(accountsRepository)),
          transactionEvents.through(transactionsListener(transactionsRepository))
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
        accountsRepository.adjustBalance(
          event.eventId.aggregateId,
          event.amount,
          event.eventId.version
        )
      case event: AccountWithdrawnEvent =>
        accountsRepository.adjustBalance(
          event.eventId.aggregateId,
          -event.amount,
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

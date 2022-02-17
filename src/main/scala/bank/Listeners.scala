package bank

import bank.model.events._
import bank.model.projection._
import bank.storage._
import zio.{Hub, Task}
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.stream._
import zio.interop.reactivestreams._
import fs2.interop.reactivestreams._
object Listeners {

  def subscribeListeners(
    eventsTopic: Hub[Event],
    accountsRepository: AccountsRepository,
    transactionsRepository: TransactionsRepository
  ): Task[fs2.Stream[Task, Unit]] =
    for {
      a <- ZStream
             .fromHub(eventsTopic, 10)
             .mapZIO(accountsListener(accountsRepository))
             .toPublisher
             .map(_.toStreamBuffered[Task](10))
      b <- ZStream
             .fromHub(eventsTopic, 10)
             .mapZIO(transactionsListener(transactionsRepository))
             .toPublisher
             .map(_.toStreamBuffered[Task](10))
    } yield a.concurrently(b)

  def accountsListener(
    accountsRepository: AccountsRepository
  )(even: Event): Task[Unit] =
    even match {
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
      case _ =>
        Task.unit
    }

  def transactionsListener(
    transactionsRepository: TransactionsRepository
  )(even: Event): Task[Unit] =
    even match {
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
      case _ =>
        Task.unit
    }
}

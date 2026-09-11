package bank

import bank.model.events._
import bank.model.projection._
import bank.storage._
import cats.effect._
import cats.syntax.apply._
import cats.syntax.flatMap._
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

  // Replays the whole event log directly into the projection repositories, through the same
  // listener logic the live topic subscription uses - the read models are a pure function of the
  // log, so this is how they get rebuilt after a restart (or after adding a brand new projection).
  // Assumes it's run against empty repositories: `save`/`transactionsRepository.save` aren't
  // idempotent, so replaying onto repositories already populated by live events would duplicate data.
  def rebuildProjections[F[_]: Async](
    eventStore: EventStore[F],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F]
  ): F[Unit] =
    eventStore.loadAll.flatMap { events =>
      fs2
        .Stream[F, fs2.Stream[F, Unit]](
          fs2.Stream.emits(events).through(accountsListener(accountsRepository)),
          fs2.Stream.emits(events).through(transactionsListener(transactionsRepository))
        )
        .parJoin(2)
        .compile
        .drain
    }

  def accountsListener[F[_]: Sync](
    accountsRepository: AccountsRepository[F]
  ): Pipe[F, Event, Unit] =
    _.evalMap {
      case event: AccountEvent =>
        event match {
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
        }
      case _: ClientEvent => Sync[F].unit
    }

  def transactionsListener[F[_]: Sync](
    transactionsRepository: TransactionsRepository[F]
  ): Pipe[F, Event, Unit] =
    _.evalMap {
      case event: AccountEvent =>
        event match {
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
          case _: AccountOpenedEvent => Sync[F].unit
        }
      case _: ClientEvent => Sync[F].unit
    }
}

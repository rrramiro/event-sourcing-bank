package bank.storage

import java.util.UUID

import bank.model.aggregates.{AggregateError, AggregateId}
import bank.model.events.Event

trait EventStore[F[_]] {
  // Fails with a Left(AggregateVersionError) on an optimistic-concurrency conflict rather than
  // raising - callers route it through the same EitherT[F, AggregateError, *] channel every other
  // domain error uses, instead of an unstructured exception bypassing HttpErrorHandler.
  def store(aggregateId: AggregateId): F[Either[AggregateError, Unit]]

  def load(aggregateId: UUID): F[List[Event]]

  // All events across all aggregates, each aggregate's own events still in version order -
  // lets read models be rebuilt from the log alone, e.g. after a restart. See Listeners.rebuildProjections.
  def loadAll: F[List[Event]]
}

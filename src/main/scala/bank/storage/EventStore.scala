package bank.storage

import java.util.UUID

import bank.model.aggregates.AggregateId
import bank.model.events.Event

trait EventStore[F[_]] {
  def store(aggregateId: AggregateId): F[Unit]

  def load(aggregateId: UUID): F[List[Event]]

  // All events across all aggregates, each aggregate's own events still in version order -
  // lets read models be rebuilt from the log alone, e.g. after a restart. See Listeners.rebuildProjections.
  def loadAll: F[List[Event]]
}

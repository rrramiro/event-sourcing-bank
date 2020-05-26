package bank.storage

import java.util.UUID

import bank.model.aggregates.AggregateId
import bank.model.events.Event

trait EventStore[F[_]] {
  def store(aggregateId: AggregateId): F[Unit]

  def load(aggregateId: UUID): F[List[Event]]
}

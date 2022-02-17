package bank.storage

import java.util.UUID
import bank.model.aggregates.AggregateId
import bank.model.events.Event
import zio.Task

trait EventStore {
  def store(aggregateId: AggregateId): Task[Unit]

  def load(aggregateId: UUID): Task[List[Event]]
}

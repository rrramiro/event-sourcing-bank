package bank.storage

import bank.model.BankError

import java.util.UUID
import bank.model.aggregates.AggregateId
import bank.model.events.Event
import zio.{IO, UIO}

trait EventStore {
  def store(aggregateId: AggregateId): IO[EvenStoreError, Unit]

  def load(aggregateId: UUID): UIO[List[Event]]
}

sealed trait EvenStoreError                                 extends BankError
final case class OptimisticLockingException(msg: String)    extends Exception(msg) with EvenStoreError
final case class UnexpectedEvenStoreError(cause: Throwable) extends Exception(cause) with EvenStoreError

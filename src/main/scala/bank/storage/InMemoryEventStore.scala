package bank.storage

import java.util.UUID
import bank.model.aggregates.AggregateId
import bank.model.events.Event
import cats.syntax.option._
import zio.{IO, UIO}

import scala.collection.concurrent.TrieMap
import scala.util.{Failure, Success, Try}

class InMemoryEventStore extends EventStore {
  private val eventStore = TrieMap.empty[UUID, List[Event]]

  @SuppressWarnings(Array("org.wartremover.warts.Throw")) //TODO resolve
  override def store(aggregateId: AggregateId): IO[EvenStoreError, Unit] =
    Try {
      val value = aggregateId.newEvents
      eventStore.updateWith(aggregateId.id)(_.fold(value) { oldValue =>
        if (
          oldValue.lastOption
            .map(_.eventId.version)
            .contains(aggregateId.baseVersion)
        )
          oldValue ++ value
        else
          throw OptimisticLockingException( //TODO Sync[F].raiseError
            "Version doesn't match with current stored version"
          )
      }.some)
    } match {
      case Failure(error: OptimisticLockingException) => IO.fail(error)
      case Failure(error)                             => IO.fail(UnexpectedEvenStoreError(error))
      case Success(_)                                 => IO.succeed(()): IO[EvenStoreError, Unit]
    }

  override def load(aggregateId: UUID): UIO[List[Event]] =
    UIO(eventStore.getOrElse(aggregateId, List.empty[Event]))
}

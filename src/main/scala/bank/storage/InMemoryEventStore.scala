package bank.storage

import java.util.UUID

import bank.model.aggregates.AggregateId
import bank.model.events.Event
import cats.effect.Sync
import cats.syntax.option._
import cats.syntax.functor._

import scala.collection.concurrent.TrieMap

object InMemoryEventStore {
  final case class OptimisticLockingException(msg: String) extends Exception(msg)
}

class InMemoryEventStore[F[_]: Sync] extends EventStore[F] {
  private val eventStore = TrieMap.empty[UUID, List[Event]]

  @SuppressWarnings(Array("org.wartremover.warts.Throw")) //TODO resolve
  override def store(aggregateId: AggregateId): F[Unit] =
    Sync[F]
      .delay {
        val value = aggregateId.newEvents
        eventStore.updateWith(aggregateId.id)(_.fold(value) { oldValue =>
          if (
            oldValue.lastOption
              .map(_.eventId.version)
              .contains(aggregateId.baseVersion)
          )
            oldValue ++ value
          else
            throw InMemoryEventStore
              .OptimisticLockingException( //TODO Sync[F].raiseError
                "Version doesn't match with current stored version"
              )
        }.some)
      }
      .as(())

  override def load(aggregateId: UUID): F[List[Event]] =
    Sync[F].delay {
      eventStore.getOrElse(aggregateId, List.empty[Event])
    }
}

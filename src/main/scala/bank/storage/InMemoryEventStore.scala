package bank.storage

import java.util.UUID

import bank.model.aggregates._
import bank.model.events.Event
import cats.effect.Sync
import cats.syntax.monadError._
import cats.syntax.option._

import scala.collection.concurrent.TrieMap
import scala.util.control.NoStackTrace

object InMemoryEventStore {
  // Signals an aborted atomic update from inside the TrieMap#updateWith callback below - caught and
  // turned into Left(AggregateVersionError) immediately, never observed outside this class.
  private case object VersionConflict extends Exception with NoStackTrace
}

class InMemoryEventStore[F[_]: Sync] extends EventStore[F] {
  private val eventStore = TrieMap.empty[UUID, List[Event]]

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def store(aggregateId: AggregateId): F[Either[AggregateError, Unit]] =
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
            throw InMemoryEventStore.VersionConflict //TODO avoid throw. raise error instead
        }.some)
      }
      .redeemWith(
        {
          case InMemoryEventStore.VersionConflict => Sync[F].pure(Left(AggregateVersionError))
          case other                              => Sync[F].raiseError(other)
        },
        _ => Sync[F].pure(Right(()))
      )

  override def load(aggregateId: UUID): F[List[Event]] =
    Sync[F].delay {
      eventStore.getOrElse(aggregateId, List.empty[Event])
    }

  override def loadAll: F[List[Event]] =
    Sync[F].delay {
      eventStore.values.toList.flatten
    }
}

package bank.services

import java.util.UUID

import bank.model.aggregates.{Aggregate, AggregateCompanion, AggregateError}
import bank.model.events.Event
import bank.storage.{EventStore, SnapshotStore}
import cats.data.EitherT
import cats.effect.Concurrent
import cats.instances.int._
import cats.syntax.apply._
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.concurrent.Topic

abstract class EventSourcedService[F[_]: Concurrent, State, Agg <: Aggregate[State]](
  eventStore: EventStore[F],
  eventsTopic: Topic[F, Event],
  snapshotStore: SnapshotStore[F, State],
  snapshotEvery: Int,
  aggregateCompanion: AggregateCompanion[State, Agg]
) {

  type ResultT[T] = EitherT[F, AggregateError, T]

  final def load(id: UUID): ResultT[Agg] =
    EitherT.right[AggregateError](snapshotStore.load(id)) >>= { snapshot =>
      EitherT.right[AggregateError](eventStore.loadSince(id, snapshot.fold(0)(_.version))) >>= { events =>
        aggregateCompanion.load[ResultT](id)(events, snapshot.map(s => s.state -> s.version))
      }
    }

  protected final def loadProcessCommit(id: UUID)(f: Agg => ResultT[Agg]): ResultT[Agg] =
    load(id) >>= f >>= commit

  // Publishing a command's finite event stream with evalMap keeps the shared topic open for later
  // commands. The event-store write must complete before events are made visible to listeners.
  protected final def commit(aggregate: Agg): ResultT[Agg] =
    EitherT(eventStore.store(aggregate.aggregateId)) *>
      EitherT.right[AggregateError] {
        fs2
          .Stream(aggregate.aggregateId.newEvents: _*)
          .covary[F]
          .evalMap(eventsTopic.publish1)
          .compile
          .drain
          .productR(maybeSnapshot(aggregate))
          .as(aggregate)
      }

  // Snapshots only cache a committed fold result; loading remains correct without one because it
  // falls back to replaying the complete aggregate event stream.
  private def maybeSnapshot(aggregate: Agg): F[Unit] = {
    val committedVersion = aggregate.aggregateId.baseVersion + aggregate.aggregateId.newEvents.size
    if (committedVersion % snapshotEvery === 0)
      snapshotStore.save(aggregate.aggregateId.id, committedVersion, aggregate.state)
    else
      Concurrent[F].unit
  }
}

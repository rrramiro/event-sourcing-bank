package bank.services

import java.util.UUID

import bank.model.aggregates.{AggregateError, Client, ClientState}
import bank.model.commands._
import bank.model.events.Event
import bank.storage.{EventStore, SnapshotStore}
import cats.data.EitherT
import cats.effect._
import cats.instances.int._
import cats.syntax.apply._
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.concurrent.Topic

class ClientService[F[_]: Concurrent](
  eventStore: EventStore[F],
  eventsTopic: Topic[F, Event],
  snapshotStore: SnapshotStore[F, ClientState],
  snapshotEvery: Int
) {

  type ResultT[T] = EitherT[F, AggregateError, T]

  def load(id: UUID): ResultT[Client] =
    EitherT.right[AggregateError](snapshotStore.load(id)) >>= { snapshot =>
      EitherT.right[AggregateError](eventStore.loadSince(id, snapshot.fold(0)(_.version))) >>= { events =>
        Client.load[ResultT](id)(events, snapshot.map(s => s.state -> s.version))
      }
    }

  def process(cmd: ClientCommand): ResultT[Client] =
    cmd match {
      case EnrollClientCommand(name, email) =>
        Client.enroll[ResultT](UUID.randomUUID(), name, email) >>= storeAndPublishEvents
      case UpdateClientCommand(id, name, email) =>
        load(id) >>= Client.update[ResultT](name, email) >>= storeAndPublishEvents
    }

  // Mirrors AccountService.storeAndPublishEvents - client events flow through the same topic/
  // Listeners pipeline as account events do, for consistency, even though no projection currently
  // reads client events (both listeners just no-op on ClientEvent today).
  private def storeAndPublishEvents(client: Client): ResultT[Client] =
    EitherT(eventStore.store(client.aggregateId)) *>
      EitherT.right[AggregateError] {
        fs2
          .Stream(client.aggregateId.newEvents: _*)
          .covary[F]
          .evalMap(eventsTopic.publish1)
          .compile
          .drain
          .productR(maybeSnapshot(client))
          .as(client)
      }

  // Mirrors AccountService.maybeSnapshot - see there for why this is purely an optimization.
  private def maybeSnapshot(client: Client): F[Unit] = {
    val committedVersion = client.aggregateId.baseVersion + client.aggregateId.newEvents.size
    if (committedVersion % snapshotEvery === 0)
      snapshotStore.save(client.aggregateId.id, committedVersion, client.state)
    else
      Concurrent[F].unit
  }
}

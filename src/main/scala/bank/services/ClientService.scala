package bank.services

import java.util.UUID

import bank.model.aggregates.{Client, ClientState}
import bank.model.commands._
import bank.model.events.Event
import bank.storage.{EventStore, SnapshotStore}
import cats.effect._
import cats.syntax.flatMap._
import fs2.concurrent.Topic

class ClientService[F[_]: Concurrent](
  eventStore: EventStore[F],
  eventsTopic: Topic[F, Event],
  snapshotStore: SnapshotStore[F, ClientState],
  snapshotEvery: Int
) extends EventSourcedService[F, ClientState, Client](
    eventStore,
    eventsTopic,
    snapshotStore,
    snapshotEvery,
    Client
  ) {

  def process(cmd: ClientCommand): ResultT[Client] =
    cmd match {
      case EnrollClientCommand(name, email) =>
        Client.enroll[ResultT](UUID.randomUUID(), name, email) >>= commit
      case UpdateClientCommand(id, name, email) =>
        loadProcessCommit(id)(Client.update[ResultT](name, email))
    }
}

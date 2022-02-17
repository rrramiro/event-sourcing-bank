package bank.services

import java.util.UUID
import bank.model.aggregates.{AggregateUnexpectedError, Client}
import bank.model.commands._
import bank.storage.EventStore
import zio.{IO, Task}

class ClientService(eventStore: EventStore) {

  def load(id: UUID): Task[Client] =
    eventStore.load(id) flatMap Client.load(id)

  def process(cmd: Command): Task[Client] =
    cmd match {
      case EnrollClientCommand(name, email) =>
        Client.enroll(UUID.randomUUID(), name, email) flatMap storeEvents
      case UpdateClientCommand(id, name, email) =>
        load(id) flatMap Client.update(name, email) flatMap storeEvents
      case _ => IO.fail(AggregateUnexpectedError)
    }

  private def storeEvents(client: Client): Task[Client] =
    eventStore.store(client.aggregateId).as(client)
}

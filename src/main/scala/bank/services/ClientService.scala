package bank.services

import java.util.UUID
import bank.model.aggregates.{AggregateError, AggregateUnexpectedError, Client}
import bank.model.commands._
import bank.storage.EventStore
import cats.effect.Sync
import cats.mtl.Raise
import cats.syntax.flatMap._
import cats.syntax.functor._

class ClientService[F[_]: Sync](eventStore: EventStore[F]) {

  def load(id: UUID)(implicit F: Raise[F, AggregateError]): F[Client] =
    eventStore.load(id) >>= Client.load[F](id)

  def process(cmd: Command)(implicit F: Raise[F, AggregateError]): F[Client] =
    cmd match {
      case EnrollClientCommand(name, email) =>
        Client.enroll[F](UUID.randomUUID(), name, email) >>= storeEvents
      case UpdateClientCommand(id, name, email) =>
        load(id) >>= Client.update[F](name, email) >>= storeEvents
      case _ => F.raise(AggregateUnexpectedError)
    }

  private def storeEvents(client: Client): F[Client] =
    eventStore.store(client.aggregateId).as(client)
}

package bank.services

import java.util.UUID

import bank.model.aggregates.{AggregateError, Client}
import bank.model.commands._
import bank.storage.EventStore
import cats.data.EitherT
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._

class ClientService[F[_]: Sync](eventStore: EventStore[F]) {

  type ResultT[T] = EitherT[F, AggregateError, T]

  def load(id: UUID): ResultT[Client] =
    EitherT.right[AggregateError](eventStore.load(id)) >>= Client.load[ResultT](id)

  def process(cmd: Command): ResultT[Client] =
    cmd match {
      case EnrollClientCommand(name, email) =>
        Client.enroll[ResultT](UUID.randomUUID(), name, email) >>= storeEvents
      case UpdateClientCommand(id, name, email) =>
        load(id) >>= Client.update[ResultT](name, email) >>= storeEvents
    }

  private def storeEvents(client: Client): ResultT[Client] =
    EitherT.right[AggregateError](eventStore.store(client.aggregateId).as(client))
}

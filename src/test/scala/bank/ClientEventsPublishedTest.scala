package bank

import bank.model.Email
import bank.model.aggregates.ClientState
import bank.model.commands.{EnrollClientCommand, UpdateClientCommand}
import bank.model.events.{ClientEnrolledEvent, ClientUpdatedEvent, Event}
import bank.services.ClientService
import bank.storage.{InMemoryEventStore, InMemorySnapshotStore}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import eu.timepit.refined.auto._
import fs2.concurrent.Topic
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration._

// Regression test for ClientService now publishing to the shared events topic just like
// AccountService does - previously ClientEnrolledEvent/ClientUpdatedEvent never left EventStore.
class ClientEventsPublishedTest extends AnyFunSuite {
  test("consecutive client commands publish their events in order without closing the shared topic") {
    val eventStore    = new InMemoryEventStore[IO]
    val snapshotStore = new InMemorySnapshotStore[IO, ClientState]

    val received = Topic[IO, Event]
      .flatMap { topic =>
        topic.subscribeAwait(10).use { events =>
          for {
            fiber <- events.take(2).compile.toList.start
            service = new ClientService[IO](eventStore, topic, snapshotStore, snapshotEvery = 5)
            enrolled <- service.process(EnrollClientCommand("Jane Doe", Email("jane@doe.com"))).value
            client = enrolled.getOrElse(fail("enroll failed"))
            _ <- service
                   .process(UpdateClientCommand(client.aggregateId.id, "Janet Doe", Email("janet@doe.com")))
                   .value
            result <- fiber.joinWithNever.timeout(2.seconds)
          } yield result
        }
      }
      .unsafeRunSync()

    assert(received.map(_.eventId.version) == List(1, 2))
    assert(received.map(_.eventId.aggregateId).distinct.size == 1)
    assert(received match {
      case List(_: ClientEnrolledEvent, _: ClientUpdatedEvent) => true
      case _                                                   => false
    })
  }
}

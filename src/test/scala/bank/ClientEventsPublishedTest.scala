package bank

import bank.model.Email
import bank.model.aggregates.ClientState
import bank.model.commands.EnrollClientCommand
import bank.model.events.{ClientEnrolledEvent, Event}
import bank.services.ClientService
import bank.storage.{InMemoryEventStore, InMemorySnapshotStore}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import eu.timepit.refined.auto._
import fs2.concurrent.Topic
import org.scalatest.funsuite.AnyFunSuite

// Regression test for ClientService now publishing to the shared events topic just like
// AccountService does - previously ClientEnrolledEvent/ClientUpdatedEvent never left EventStore.
class ClientEventsPublishedTest extends AnyFunSuite {
  test("enrolling a client publishes ClientEnrolledEvent onto the shared events topic") {
    val eventStore    = new InMemoryEventStore[IO]
    val snapshotStore = new InMemorySnapshotStore[IO, ClientState]

    val received = Topic[IO, Event]
      .flatMap { topic =>
        topic.subscribeAwait(10).use { events =>
          for {
            fiber <- events.take(1).compile.toList.start
            service = new ClientService[IO](eventStore, topic, snapshotStore, snapshotEvery = 5)
            _      <- service.process(EnrollClientCommand("Jane Doe", Email("jane@doe.com"))).value
            result <- fiber.joinWithNever
          } yield result
        }
      }
      .unsafeRunSync()

    assert(received.collect { case e: ClientEnrolledEvent => e }.nonEmpty)
  }
}

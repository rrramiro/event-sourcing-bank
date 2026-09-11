package bank.storage

import java.time.ZonedDateTime
import java.util.UUID

import bank.model.aggregates.{AggregateId, AggregateVersionError}
import bank.model.events.{AccountOpenedEvent, EventId}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.funsuite.AnyFunSuite

class InMemoryEventStoreTest extends AnyFunSuite {
  private def openedEvent(aggregateId: UUID, version: Int): AccountOpenedEvent =
    AccountOpenedEvent(UUID.randomUUID(), BigDecimal(0), EventId(version, aggregateId, ZonedDateTime.now()))

  test("store succeeds when baseVersion matches the current stream head") {
    val eventStore = new InMemoryEventStore[IO]
    val id         = UUID.randomUUID()

    val result = eventStore.store(AggregateId(id, 0, List(openedEvent(id, 1)))).unsafeRunSync()

    assert(result == Right(()))
  }

  test("store reports a stale baseVersion as Left(AggregateVersionError), not a raised exception") {
    val eventStore = new InMemoryEventStore[IO]
    val id         = UUID.randomUUID()

    val first = eventStore.store(AggregateId(id, 0, List(openedEvent(id, 1)))).unsafeRunSync()
    assert(first == Right(()))

    // Simulates a second command that loaded the aggregate before the first one committed: it also
    // thinks the stream is still at version 0, i.e. the genuine optimistic-concurrency race.
    val conflicting = eventStore.store(AggregateId(id, 0, List(openedEvent(id, 1)))).unsafeRunSync()

    assert(conflicting == Left(AggregateVersionError))
  }
}

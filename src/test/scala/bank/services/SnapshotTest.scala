package bank.services

import java.util.UUID

import bank.model.aggregates.{AccountState, AggregateId}
import bank.model.commands._
import bank.model.events.Event
import bank.storage._
import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import fs2.concurrent.Topic
import org.scalatest.funsuite.AnyFunSuite

// Records the `afterVersion` argument every `loadSince` call actually used, so a test can assert
// that a snapshot-aware load skips straight to "events since the snapshot" instead of the full log.
private class RecordingEventStore(underlying: EventStore[IO], calls: Ref[IO, List[Int]]) extends EventStore[IO] {
  override def store(aggregateId: AggregateId): IO[Either[bank.model.aggregates.AggregateError, Unit]] =
    underlying.store(aggregateId)

  override def loadSince(aggregateId: UUID, afterVersion: Int): IO[List[Event]] =
    calls.update(_ :+ afterVersion) *> underlying.loadSince(aggregateId, afterVersion)

  override def loadAll: IO[List[Event]] = underlying.loadAll
}

class SnapshotTest extends AnyFunSuite {
  test("a snapshot is written every `snapshotEvery` committed versions") {
    val eventStore    = new InMemoryEventStore[IO]
    val snapshotStore = new InMemorySnapshotStore[IO, AccountState]

    val program = for {
      topic  <- Topic[IO, Event]
      service = new AccountService[IO](eventStore, topic, snapshotStore, snapshotEvery = 3)
      opened <- service.process(OpenAccountCommand(UUID.randomUUID())).value // v1
      account = opened.getOrElse(fail("open failed"))
      id      = account.aggregateId.id
      _      <- service.process(DepositAccountCommand(id, 1)).value // v2
      before <- snapshotStore.load(id)
      _      <- service.process(DepositAccountCommand(id, 1)).value // v3 -> snapshot
      after  <- snapshotStore.load(id)
    } yield (before, after)

    val (before, after) = program.unsafeRunSync()

    assert(before.isEmpty, "no snapshot should exist before the version threshold is reached")
    assert(after.map(_.version) == Some(3))
    assert(after.map(_.state.balance) == Some(BigDecimal(2)))
  }

  test("load resumes from the snapshot instead of replaying the full event log") {
    val program = for {
      calls        <- Ref.of[IO, List[Int]](List.empty)
      underlying    = new InMemoryEventStore[IO]
      eventStore    = new RecordingEventStore(underlying, calls)
      snapshotStore = new InMemorySnapshotStore[IO, AccountState]
      topic        <- Topic[IO, Event]
      service       = new AccountService[IO](eventStore, topic, snapshotStore, snapshotEvery = 3)
      opened       <- service.process(OpenAccountCommand(UUID.randomUUID())).value // v1
      account       = opened.getOrElse(fail("open failed"))
      id            = account.aggregateId.id
      _            <- service.process(DepositAccountCommand(id, 1)).value // v2
      _            <- service.process(DepositAccountCommand(id, 1)).value // v3 -> snapshot
      _            <- service.process(DepositAccountCommand(id, 1)).value // v4
      _            <- calls.set(List.empty)
      loaded       <- service.load(id).value
      recordedCalls <- calls.get
    } yield (loaded, recordedCalls)

    val (loaded, recordedCalls) = program.unsafeRunSync()

    assert(loaded.map(_.state.balance) == Right(BigDecimal(3)))
    // Every loadSince call after the snapshot was taken (version 3) asked for events after
    // version 3, never after 0 - i.e. it never fell back to the full log.
    assert(recordedCalls == List(3))
  }
}

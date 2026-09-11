package bank.storage

import java.util.UUID

final case class Snapshot[State](version: Int, state: State)

// Lets a *Service.load skip straight to "events since the snapshot" instead of replaying an
// aggregate's full history on every read - the standard next step for a real event-sourced system
// once event streams get long. State is derived from the log either way; a snapshot is purely a
// cache of a fold result at a known version, never a second source of truth.
trait SnapshotStore[F[_], State] {
  def save(aggregateId: UUID, version: Int, state: State): F[Unit]

  def load(aggregateId: UUID): F[Option[Snapshot[State]]]
}

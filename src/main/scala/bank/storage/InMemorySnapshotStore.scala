package bank.storage

import java.util.UUID

import cats.effect.Sync
import cats.syntax.functor._

import scala.collection.concurrent.TrieMap

class InMemorySnapshotStore[F[_]: Sync, State] extends SnapshotStore[F, State] {
  private val snapshots = TrieMap.empty[UUID, Snapshot[State]]

  override def save(aggregateId: UUID, version: Int, state: State): F[Unit] =
    Sync[F].delay(snapshots.put(aggregateId, Snapshot(version, state))).void

  override def load(aggregateId: UUID): F[Option[Snapshot[State]]] =
    Sync[F].delay(snapshots.get(aggregateId))
}

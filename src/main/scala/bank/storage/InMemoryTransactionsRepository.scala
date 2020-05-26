package bank.storage

import java.util.UUID
import scala.collection.concurrent.TrieMap

import bank.model.projection.TransactionProjection
import cats.effect.Sync
import cats.syntax.option._
import cats.syntax.functor._

class InMemoryTransactionsRepository[F[_]: Sync] extends TransactionsRepository[F] {
  private val accountTransactions =
    TrieMap.empty[UUID, List[TransactionProjection]]

  override def listByAccount(accountId: UUID): F[List[TransactionProjection]] =
    Sync[F].delay {
      accountTransactions
        .getOrElse(accountId, List.empty[TransactionProjection])
        .sortBy(_.version)
    }

  override def save(transactionProjection: TransactionProjection): F[Unit] =
    Sync[F]
      .delay {
        val value = List(transactionProjection)
        accountTransactions.updateWith(transactionProjection.accountId)(
          _.fold(value)(_ ++ value).some
        )
      }
      .as(())
}

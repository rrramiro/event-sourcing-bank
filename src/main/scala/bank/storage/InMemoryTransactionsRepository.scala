package bank.storage

import java.util.UUID
import scala.collection.concurrent.TrieMap
import bank.model.projection.TransactionProjection
import cats.syntax.option._
import zio.Task

class InMemoryTransactionsRepository extends TransactionsRepository {
  private val accountTransactions =
    TrieMap.empty[UUID, List[TransactionProjection]]

  override def listByAccount(accountId: UUID): Task[List[TransactionProjection]] =
    Task {
      accountTransactions
        .getOrElse(accountId, List.empty[TransactionProjection])
        .sortBy(_.version)
    }

  override def save(transactionProjection: TransactionProjection): Task[Unit] =
    Task {
      val value = List(transactionProjection)
      accountTransactions.updateWith(transactionProjection.accountId)(
        _.fold(value)(_ ++ value).some
      )
    }.as(())
}

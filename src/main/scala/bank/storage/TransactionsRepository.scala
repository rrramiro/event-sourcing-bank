package bank.storage

import java.util.UUID

import bank.model.projection.TransactionProjection

trait TransactionsRepository[F[_]] {
  def save(transactionProjection: TransactionProjection): F[Unit]

  def listByAccount(accountId: UUID): F[List[TransactionProjection]]
}

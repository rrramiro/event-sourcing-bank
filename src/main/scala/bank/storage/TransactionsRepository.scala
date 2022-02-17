package bank.storage

import java.util.UUID
import bank.model.projection.TransactionProjection
import zio.Task

trait TransactionsRepository {
  def save(transactionProjection: TransactionProjection): Task[Unit]

  def listByAccount(accountId: UUID): Task[List[TransactionProjection]]
}

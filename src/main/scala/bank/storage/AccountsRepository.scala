package bank.storage

import java.util.UUID
import bank.model.projection.AccountProjection
import zio.Task

trait AccountsRepository {
  def save(accountProjection: AccountProjection): Task[Unit]

  def updateBalance(
    accountId: UUID,
    balance: BigDecimal,
    version: Int
  ): Task[Unit]

  def getAccounts(clientId: UUID): Task[List[AccountProjection]]
}

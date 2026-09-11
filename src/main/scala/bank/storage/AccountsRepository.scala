package bank.storage

import java.util.UUID

import bank.model.projection.AccountProjection

trait AccountsRepository[F[_]] {
  def save(accountProjection: AccountProjection): F[Unit]

  def adjustBalance(
    accountId: UUID,
    delta: BigDecimal,
    version: Int
  ): F[Unit]

  def getAccounts(clientId: UUID): F[List[AccountProjection]]
}

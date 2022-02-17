package bank.storage

import java.util.UUID
import bank.model.projection.AccountProjection
import cats.syntax.option._
import zio.Task

import scala.collection.concurrent.TrieMap

class InMemoryAccountsRepository extends AccountsRepository {
  private val clientAccounts =
    TrieMap.empty[UUID, Map[UUID, AccountProjection]]
  private val accountClientIndex = TrieMap.empty[UUID, UUID]

  override def save(accountProjection: AccountProjection): Task[Unit] =
    Task {
      val value = Map(accountProjection.accountId -> accountProjection)
      clientAccounts.updateWith(accountProjection.clientId)(
        _.fold(value)(_ ++ value).some
      )
    } *> Task {
      accountClientIndex
        .put(accountProjection.accountId, accountProjection.clientId)
    }.as(())

  override def updateBalance(
    accountId: UUID,
    balance: BigDecimal,
    version: Int
  ): Task[Unit] =
    Task {
      accountClientIndex.get(accountId).map { clientId =>
        val value = AccountProjection(accountId, clientId, balance, version)
        clientAccounts.updateWith(clientId)(
          _.fold(Map(accountId -> value))(_.updatedWith(accountId)(_.fold(value) { oldValue =>
            if (oldValue.version >= value.version) oldValue else value
          }.some)).some
        )
      }
    }.as(())

  override def getAccounts(clientId: UUID): Task[List[AccountProjection]] =
    Task {
      clientAccounts
        .get(clientId)
        .map(_.values.toList)
        .getOrElse(List.empty[AccountProjection])
    }
}

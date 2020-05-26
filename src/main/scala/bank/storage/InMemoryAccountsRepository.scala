package bank.storage

import java.util.UUID

import bank.model.projection.AccountProjection
import cats.effect.Sync
import cats.syntax.option._
import cats.syntax.apply._
import cats.syntax.functor._

import scala.collection.concurrent.TrieMap

class InMemoryAccountsRepository[F[_]: Sync] extends AccountsRepository[F] {
  private val clientAccounts =
    TrieMap.empty[UUID, Map[UUID, AccountProjection]]
  private val accountClientIndex = TrieMap.empty[UUID, UUID]

  override def save(accountProjection: AccountProjection): F[Unit] =
    Sync[F].delay {
      val value = Map(accountProjection.accountId -> accountProjection)
      clientAccounts.updateWith(accountProjection.clientId)(
        _.fold(value)(_ ++ value).some
      )
    } *> Sync[F]
      .delay {
        accountClientIndex
          .put(accountProjection.accountId, accountProjection.clientId)
      }
      .as(())

  override def updateBalance(
    accountId: UUID,
    balance: BigDecimal,
    version: Int
  ): F[Unit] =
    Sync[F]
      .delay {
        accountClientIndex.get(accountId).map { clientId =>
          val value = AccountProjection(accountId, clientId, balance, version)
          clientAccounts.updateWith(clientId)(
            _.fold(Map(accountId -> value))(_.updatedWith(accountId)(_.fold(value) { oldValue =>
              if (oldValue.version >= value.version) oldValue else value
            }.some)).some
          )
        }
      }
      .as(())

  override def getAccounts(clientId: UUID): F[List[AccountProjection]] =
    Sync[F].delay {
      clientAccounts
        .get(clientId)
        .map(_.values.toList)
        .getOrElse(List.empty[AccountProjection])
    }
}

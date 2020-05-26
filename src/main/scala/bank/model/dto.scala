package bank.model

import java.util.UUID

import cats.effect.Sync
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf
import io.circe.generic.auto._

object dto {

  final case class AccountDto(id: UUID, balance: BigDecimal, clientId: UUID)
  object AccountDto {
    implicit def decoder[F[_]: Sync]: EntityDecoder[F, AccountDto] = jsonOf[F, AccountDto]
  }

  final case class WithdrawalDto(accountId: UUID, amount: BigDecimal)
  object WithdrawalDto {
    implicit def decoder[F[_]: Sync]: EntityDecoder[F, WithdrawalDto] = jsonOf[F, WithdrawalDto]
  }

  final case class DepositDto(accountId: UUID, amount: BigDecimal)
  object DepositDto {
    implicit def decoder[F[_]: Sync]: EntityDecoder[F, DepositDto] = jsonOf[F, DepositDto]
  }

  final case class ClientDto(id: UUID, name: String, email: Email)
  object ClientDto {
    implicit def decoder[F[_]: Sync]: EntityDecoder[F, ClientDto] = jsonOf[F, ClientDto]
  }

}

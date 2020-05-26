package bank.routes

import bank.model.aggregates._
import bank.model.commands._
import bank.model.dto._
import bank.services._
import bank.storage._
import cats.data._
import cats.effect.Sync
import cats.syntax.semigroupk._
import cats.syntax.flatMap._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class BankRoutes[F[_]: Sync](
  accountService: AccountService[F],
  clientService: ClientService[F],
  accountsRepository: AccountsRepository[F],
  transactionsRepository: TransactionsRepository[F]
) extends Http4sDsl[EitherT[F, AggregateError, *]] {

  implicit class AccountOps(account: Account) {
    def toDto: AccountDto =
      AccountDto(
        account.aggregateId.id,
        account.state.balance,
        account.state.clientId
      )
  }

  implicit class ClientOps(client: Client) {
    def toDto: ClientDto =
      ClientDto(
        client.aggregateId.id,
        client.state.name,
        client.state.email
      )
  }

  type ResultT[T] = EitherT[F, AggregateError, T]

  val accountRoutes: HttpRoutes[ResultT] =
    HttpRoutes.of[ResultT] {
      case req @ POST -> Root / "accounts" =>
        req.as[AccountDto] >>= (dto => accountService.process(OpenAccountCommand(dto.clientId))) >>= (account =>
          Ok(account.toDto.asJson)
        )
      case GET -> Root / "accounts" / UUIDVar(uuid) =>
        accountService.load(uuid) >>= (account => Ok(account.toDto.asJson))
      case req @ POST -> Root / "accounts" / UUIDVar(uuid) / "deposits" =>
        req.as[DepositDto] >>= (dto => accountService.process(DepositAccountCommand(uuid, dto.amount))) >>= (account =>
          Ok(account.toDto.asJson)
        )
      case req @ POST -> Root / "accounts" / UUIDVar(uuid) / "withdrawals" =>
        req.as[DepositDto] >>= (dto => accountService.process(WithdrawAccountCommand(uuid, dto.amount))) >>= (account =>
          Ok(account.toDto.asJson)
        )
    }

  val clientRoutes: HttpRoutes[ResultT] =
    HttpRoutes.of[ResultT] {
      case req @ POST -> Root / "clients" =>
        req.as[ClientDto] >>= (dto => clientService.process(EnrollClientCommand(dto.name, dto.email))) >>= (client =>
          Ok(client.toDto.asJson)
        )
      case GET -> Root / "clients" / UUIDVar(uuid) =>
        clientService.load(uuid) >>= (client => Ok(client.toDto.asJson))
      case req @ PUT -> Root / "clients" / UUIDVar(uuid) =>
        req.as[ClientDto] >>= (dto => clientService.process(UpdateClientCommand(uuid, dto.name, dto.email))) >>= (
          client => Ok(client.toDto.asJson)
        )
    }

  val projections: HttpRoutes[ResultT] =
    HttpRoutes.of[ResultT] {
      case GET -> Root / "accounts" / UUIDVar(accountId) / "transactions" =>
        EitherT.right[AggregateError](transactionsRepository.listByAccount(accountId)) >>= (dto => Ok(dto.asJson))
      case GET -> Root / "clients" / UUIDVar(clientId) / "accounts" =>
        EitherT.right[AggregateError](accountsRepository.getAccounts(clientId)) >>= (dto => Ok(dto.asJson))
    }

  val routes: HttpRoutes[ResultT] = accountRoutes <+> clientRoutes <+> projections

}

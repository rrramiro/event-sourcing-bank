package bank.routes

import bank.model.aggregates._
import bank.model.commands._
import bank.model.dto._
import bank.services._
import bank.storage._
import cats.syntax.semigroupk._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import zio.Task
import zio.interop.catz._
import zio.interop.catz.implicits._

class BankRoutes(
  accountService: AccountService,
  clientService: ClientService,
  accountsRepository: AccountsRepository,
  transactionsRepository: TransactionsRepository
) extends Http4sDsl[Task] {

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

  def accountRoutes: HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "accounts" =>
        req.as[AccountDto] flatMap (dto => accountService.process(OpenAccountCommand(dto.clientId))) flatMap (account =>
          Ok(account.toDto.asJson)
        )
      case GET -> Root / "accounts" / UUIDVar(uuid) =>
        accountService.load(uuid) flatMap (account => Ok(account.toDto.asJson))
      case req @ POST -> Root / "accounts" / UUIDVar(uuid) / "deposits" =>
        req.as[DepositDto] flatMap (dto => accountService.process(DepositAccountCommand(uuid, dto.amount))) flatMap (
          account => Ok(account.toDto.asJson)
        )
      case req @ POST -> Root / "accounts" / UUIDVar(uuid) / "withdrawals" =>
        req.as[DepositDto] flatMap (dto => accountService.process(WithdrawAccountCommand(uuid, dto.amount))) flatMap (
          account => Ok(account.toDto.asJson)
        )
    }

  def clientRoutes: HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "clients" =>
        req.as[ClientDto] flatMap (dto => clientService.process(EnrollClientCommand(dto.name, dto.email))) flatMap (
          client => Ok(client.toDto.asJson)
        )
      case GET -> Root / "clients" / UUIDVar(uuid) =>
        clientService.load(uuid) flatMap (client => Ok(client.toDto.asJson))
      case req @ PUT -> Root / "clients" / UUIDVar(uuid) =>
        req.as[ClientDto] flatMap (dto =>
          clientService.process(UpdateClientCommand(uuid, dto.name, dto.email))
        ) flatMap (client => Ok(client.toDto.asJson))
    }

  val projections: HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case GET -> Root / "accounts" / UUIDVar(accountId) / "transactions" =>
        transactionsRepository.listByAccount(accountId) flatMap (dto => Ok(dto.asJson))
      case GET -> Root / "clients" / UUIDVar(clientId) / "accounts" =>
        accountsRepository.getAccounts(clientId) flatMap (dto => Ok(dto.asJson))
    }

  val routes: HttpRoutes[Task] = HttpErrorHandler[AggregateError] {
    accountRoutes <+> clientRoutes <+> projections
  } {
    case AggregateNotFound        => NotFound()
    case AggregateVersionError    => InternalServerError()
    case AggregateUnexpectedError => InternalServerError()
  }
}

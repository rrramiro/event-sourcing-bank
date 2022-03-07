package bank.routes

import bank.model.aggregates._
import bank.model.dto._
import bank.services._
import bank.storage._
import cats.syntax.semigroupk._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server._
import zio.Task
import zio.interop.catz._
import zio.interop.catz.implicits._

class BankApi(
  accountService: AccountService,
  clientService: ClientService,
  accountsRepository: AccountsRepository,
  transactionsRepository: TransactionsRepository
) extends Http4sDsl[Task]
  with DtoCmdHelpers {

  def accountRoutes: HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "accounts" =>
        req.as[AccountDto].map(_.toCmd) flatMap accountService.process flatMap (account => Ok(account.toDto.asJson))
      case GET -> Root / "accounts" / UUIDVar(uuid) =>
        accountService.load(uuid) flatMap (account => Ok(account.toDto.asJson))
      case req @ POST -> Root / "accounts" / UUIDVar(uuid) / "deposits" =>
        req.as[DepositDto].map(_.toCmd(uuid)) flatMap accountService.process flatMap (account =>
          Ok(account.toDto.asJson)
        )
      case req @ POST -> Root / "accounts" / UUIDVar(uuid) / "withdrawals" =>
        req.as[WithdrawalDto].map(_.toCmd(uuid)) flatMap accountService.process flatMap (account =>
          Ok(account.toDto.asJson)
        )
    }

  def clientRoutes: HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "clients" =>
        req.as[ClientDto].map(_.enrollCmd) flatMap clientService.process flatMap (client => Ok(client.toDto.asJson))
      case GET -> Root / "clients" / UUIDVar(uuid) =>
        clientService.load(uuid) flatMap (client => Ok(client.toDto.asJson))
      case req @ PUT -> Root / "clients" / UUIDVar(uuid) =>
        req.as[ClientDto].map(_.updateCmd(uuid)) flatMap clientService.process flatMap (client =>
          Ok(client.toDto.asJson)
        )
    }

  val projections: HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case GET -> Root / "accounts" / UUIDVar(accountId) / "transactions" =>
        transactionsRepository.listByAccount(accountId) flatMap (dto => Ok(dto.asJson))
      case GET -> Root / "clients" / UUIDVar(clientId) / "accounts" =>
        accountsRepository.getAccounts(clientId) flatMap (dto => Ok(dto.asJson))
    }

  val routes: HttpRoutes[Task] = HttpErrorHandler[AggregateError](
    accountRoutes <+> clientRoutes <+> projections
  ) {
    case AggregateNotFound        => NotFound()
    case AggregateVersionError    => InternalServerError()
    case AggregateUnexpectedError => InternalServerError()
  }

  val httpApp: HttpApp[Task] = Router("/api" -> routes).orNotFound

}

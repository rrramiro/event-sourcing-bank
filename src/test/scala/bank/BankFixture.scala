package bank

import java.util.UUID

import bank.model.aggregates.{AccountState, ClientState}
import bank.model.dto._
import bank.model.events.Event
import bank.routes.{BankApp, BankRoutes}
import bank.services._
import bank.storage._
import cats.effect._
import cats.syntax.either._
import fs2.concurrent.Topic
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.client.{Client => Http4sClient}
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuiteLike
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.circe._
import sttp.client3.http4s.Http4sBackend
import sttp.model.StatusCode
import cats.effect.unsafe.implicits.global

import scala.concurrent.duration._

trait BankFixture { self: AsyncFunSuiteLike =>
  private val eventStore             = new InMemoryEventStore[IO]
  private val transactionsRepository = new InMemoryTransactionsRepository[IO]
  private val accountsRepository     = new InMemoryAccountsRepository[IO]
  private val accountSnapshotStore   = new InMemorySnapshotStore[IO, AccountState]
  private val clientSnapshotStore    = new InMemorySnapshotStore[IO, ClientState]

  private def createBackend[F[_]: Async](
    topic: Topic[F, Event],
    eventStore: InMemoryEventStore[F],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F],
    accountSnapshotStore: SnapshotStore[F, AccountState],
    clientSnapshotStore: SnapshotStore[F, ClientState],
    snapshotEvery: Int = 5
  ): SttpBackend[F, Fs2Streams[F]] = {
    val bankRoutes = new BankApp[F](
      new BankRoutes[F](
        new AccountService[F](eventStore, topic, accountSnapshotStore, snapshotEvery),
        new ClientService[F](eventStore, topic, clientSnapshotStore, snapshotEvery),
        accountsRepository,
        transactionsRepository
      ).routes
    )
    Http4sBackend.usingClient[F](
      Http4sClient.fromHttpApp[F](
        bankRoutes.router
      )
    )
  }

  // Projections are populated asynchronously off the event stream (see CLAUDE.md), so a read
  // right after a write can observe stale state - poll until it catches up, rather than assuming
  // the projection update has already landed by the time the write's HTTP response comes back.
  def eventually[A](io: IO[A])(cond: A => Boolean, maxAttempts: Int = 20, delay: FiniteDuration = 20.millis): IO[A] =
    io.flatMap { a =>
      if (cond(a) || maxAttempts <= 1) IO.pure(a)
      else IO.sleep(delay) *> eventually(io)(cond, maxAttempts - 1, delay)
    }

  def asJsonOrFail[B: Decoder: IsOption]: ResponseAs[B, Any] =
    asJson.mapWithMetadata { (body, meta) =>
      if (meta.code != StatusCode.Ok) fail(s"error code: ${meta.code}")
      else body.valueOr(error => fail(error.toString))
    }

  def enrollClient(backend: SttpBackend[IO, Fs2Streams[IO]], client: ClientDto): IO[ClientDto] =
    basicRequest
      .post(uri"http://localhost/api/clients")
      .body(client.asJson.toString())
      .response(asJsonOrFail[ClientDto])
      .send(backend)
      .map(_.body)

  def openAccount(backend: SttpBackend[IO, Fs2Streams[IO]], clientId: UUID): IO[AccountDto] = {
    val account = AccountDto(UUID.randomUUID(), 0, clientId)
    basicRequest
      .post(uri"http://localhost/api/accounts")
      .body(account.asJson.toString())
      .response(asJsonOrFail[AccountDto])
      .send(backend)
      .map(_.body)
  }

  def depositInto(
    backend: SttpBackend[IO, Fs2Streams[IO]],
    accountId: UUID,
    amount: BigDecimal
  ): IO[AccountDto] =
    postAmount(backend, accountId, "deposits", amount, asJsonOrFail[AccountDto]).map(_.body)

  def withdrawFrom(
    backend: SttpBackend[IO, Fs2Streams[IO]],
    accountId: UUID,
    amount: BigDecimal
  ): IO[AccountDto] =
    postAmount(backend, accountId, "withdrawals", amount, asJsonOrFail[AccountDto]).map(_.body)

  def withdrawalStatus(
    backend: SttpBackend[IO, Fs2Streams[IO]],
    accountId: UUID,
    amount: BigDecimal
  ): IO[StatusCode] =
    postAmount(backend, accountId, "withdrawals", amount, sttp.client3.ignore).map(_.code)

  private def postAmount[B](
    backend: SttpBackend[IO, Fs2Streams[IO]],
    accountId: UUID,
    operation: String,
    amount: BigDecimal,
    responseAs: ResponseAs[B, Any]
  ): IO[Response[B]] = {
    val request = DepositDto(accountId, amount)
    basicRequest
      .post(uri"http://localhost/api/accounts/$accountId/$operation")
      .body(request.asJson.toString())
      .response(responseAs)
      .send(backend)
  }

  def testApp(testName: String)(
    f: SttpBackend[IO, Fs2Streams[IO]] => IO[Assertion]
  ): Unit =
    test(testName) {
      (for {
        topic <- Topic[IO, Event]
        assertion <- Listeners
                       .subscribeListeners[IO](topic, accountsRepository, transactionsRepository)
                       .use { subs =>
                         (fs2.Stream.eval(
                           Resource
                             .make(
                               IO(
                                 createBackend(
                                   topic,
                                   eventStore,
                                   accountsRepository,
                                   transactionsRepository,
                                   accountSnapshotStore,
                                   clientSnapshotStore
                                 )
                               )
                             )(_.close())
                             .use(f)
                         ) concurrently subs).compile.last
                           .map(_.getOrElse(fail("no assertion")))
                       }
      } yield assertion).unsafeToFuture()
    }
}

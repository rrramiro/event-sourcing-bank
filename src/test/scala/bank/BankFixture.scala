package bank

import bank.model.events.Event
import bank.routes.{BankApp, BankRoutes}
import bank.services._
import bank.storage._
import cats.effect._
import cats.syntax.either._
import fs2.concurrent.Topic
import io.circe.Decoder
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

  private def createBackend[F[_]: Async](
    topic: Topic[F, Event],
    eventStore: InMemoryEventStore[F],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F]
  ): SttpBackend[F, Fs2Streams[F]] = {
    val bankRoutes = new BankApp[F](
      new BankRoutes[F](
        new AccountService[F](eventStore, topic),
        new ClientService[F](eventStore, topic),
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
                                   transactionsRepository
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

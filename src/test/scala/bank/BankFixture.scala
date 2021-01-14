package bank

import bank.model.events.{Event, InitEvent}
import bank.routes.{BankApp, BankRoutes}
import bank.services._
import bank.storage._
import cats.effect._
import cats.syntax.either._
import cats.syntax.applicativeError._
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

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait BankFixture { self: AsyncFunSuiteLike =>
  override val executionContext: ExecutionContext = ExecutionContext.global
  implicit val ioContextShift: ContextShift[IO] =
    IO.contextShift(executionContext)
  implicit val ioTimer: Timer[IO] = IO.timer(executionContext)

  private val eventStore             = new InMemoryEventStore[IO]
  private val transactionsRepository = new InMemoryTransactionsRepository[IO]
  private val accountsRepository     = new InMemoryAccountsRepository[IO]

  private def subcriptions[F[_]: Concurrent](
    topic: Topic[F, Event],
    switch: concurrent.Deferred[F, Unit],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F]
  ) =
    Listeners
      .subscribeListeners[F](
        topic,
        accountsRepository,
        transactionsRepository
      )
      .interruptWhen(switch.get.attempt)

  private def createBackend[F[_]: ConcurrentEffect: ContextShift](
    topic: Topic[F, Event],
    eventStore: InMemoryEventStore[F],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F]
  ): SttpBackend[F, Fs2Streams[F]] = {
    val bankRoutes = new BankApp[F](
      new BankRoutes[F](
        new AccountService[F](eventStore, topic),
        new ClientService[F](eventStore),
        accountsRepository,
        transactionsRepository
      ).routes
    )
    Http4sBackend.usingClient[F](
      Http4sClient.fromHttpApp[F](
        bankRoutes.router
      ),
      Blocker.liftExecutionContext(executionContext)
    )
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
      {
        for {
          switch <- fs2.Stream.eval(concurrent.Deferred[IO, Unit])
          topic  <- fs2.Stream.eval(Topic[IO, Event](InitEvent))
          subs = subcriptions[IO](
                   topic,
                   switch,
                   accountsRepository,
                   transactionsRepository
                 )
          r <- fs2.Stream.eval(
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
               ) concurrently subs
          _ <- fs2.Stream.eval(switch.complete(())).delayBy(1 second)
        } yield r
      }.compile.last.map(_.getOrElse(fail("no assertion"))).unsafeToFuture()
    }
}

package bank

import bank.model.events.{Event, InitEvent}
import bank.routes.{BankApp, BankRoutes}
import bank.services._
import bank.storage._
import cats.effect._
import cats.syntax.either._
import cats.syntax.applicativeError._
import fs2.concurrent.Topic
import org.http4s.client.{Client => Http4sClient}
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuiteLike
import sttp.client._
import sttp.client.http4s.Http4sBackend
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

  private def subcriptions[F[_]: Sync: Concurrent](
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

  private def createBackend[F[_]: Sync: ConcurrentEffect: ContextShift](
    topic: Topic[F, Event],
    eventStore: InMemoryEventStore[F],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F]
  ): SttpBackend[F, fs2.Stream[F, Byte], NothingT] = {
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

  implicit class RequestTWrapper[E, T, +S](
    requestT: RequestT[Identity, Either[E, T], S]
  ) {
    def call(implicit backend: SttpBackend[IO, S, NothingT]): IO[T] = // TODO [F[_]: Sync]
      requestT
        .send[IO]()
        .map { resp =>
          if (resp.code != StatusCode.Ok) fail(s"error code: ${resp.code}")
          else resp.body.valueOr(error => fail(error.toString))
        }
  }

  def testApp(testName: String)(
    f: SttpBackend[IO, fs2.Stream[IO, Byte], NothingT] => IO[Assertion]
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

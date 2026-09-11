package bank

import bank.model.events.{Event, InitEvent}
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

trait BankFixture { self: AsyncFunSuiteLike =>
  private val eventStore             = new InMemoryEventStore[IO]
  private val transactionsRepository = new InMemoryTransactionsRepository[IO]
  private val accountsRepository     = new InMemoryAccountsRepository[IO]

  private def subcriptions[F[_]: Async](
    topic: Topic[F, Event],
    accountsRepository: AccountsRepository[F],
    transactionsRepository: TransactionsRepository[F]
  ) =
    Listeners
      .subscribeListeners[F](
        topic,
        accountsRepository,
        transactionsRepository
      )

  private def createBackend[F[_]: Async](
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
      )
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
          topic <- fs2.Stream.eval(Topic[IO, Event])
          _     <- fs2.Stream.eval(topic.publish1(InitEvent))
          subs = subcriptions[IO](
                   topic,
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
        } yield r
      }.compile.last.map(_.getOrElse(fail("no assertion"))).unsafeToFuture()
    }
}

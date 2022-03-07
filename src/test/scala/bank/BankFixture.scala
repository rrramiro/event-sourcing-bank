package bank

import bank.model.events.Event
import bank.routes.BankApi
import bank.services._
import bank.storage._
import cats.effect.Resource
import cats.effect.Deferred
import cats.syntax.either._
import cats.syntax.applicativeError._
import org.http4s.client.{Client => Http4sClient}
import org.scalatest.Assertion
import org.scalatest.funsuite.AsyncFunSuiteLike
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.http4s.Http4sBackend
import sttp.model.StatusCode
import zio.{Hub, Task, ZEnv, ZHub}
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.duration._

trait BankFixture { self: AsyncFunSuiteLike =>
  lazy val runtime: zio.Runtime[ZEnv] = zio.Runtime.default
  private val eventStore              = new InMemoryEventStore
  private val transactionsRepository  = new InMemoryTransactionsRepository
  private val accountsRepository      = new InMemoryAccountsRepository

  private def subcriptions(
    topic: Hub[Event],
    switch: Deferred[Task, Unit],
    accountsRepository: AccountsRepository,
    transactionsRepository: TransactionsRepository
  ): Task[fs2.Stream[Task, Unit]] =
    Listeners
      .subscribeListeners(
        topic,
        accountsRepository,
        transactionsRepository
      )
      .map(
        _.interruptWhen(switch.get.attempt)
      )

  private def createBackend(
    topic: Hub[Event],
    eventStore: InMemoryEventStore,
    accountsRepository: AccountsRepository,
    transactionsRepository: TransactionsRepository
  ) = {
    val bankRoutes =
      new BankApi(
        new AccountService(eventStore, topic),
        new ClientService(eventStore),
        accountsRepository,
        transactionsRepository
      )

    Http4sBackend.usingClient[Task](
      Http4sClient.fromHttpApp[Task](
        bankRoutes.httpApp
      )
    )
  }

  implicit class RequestTWrapper[E, T, S](
    requestT: RequestT[Identity, Either[E, T], S]
  ) {
    def call(implicit backend: SttpBackend[Task, S]): Task[T] = // TODO [F[_]: Sync]
      backend
        .send(requestT)
        .map { resp =>
          if (resp.code != StatusCode.Ok) fail(s"error code: ${resp.code}")
          else resp.body.valueOr(error => fail(error.toString))
        }
  }

  def testApp(testName: String)(
    f: SttpBackend[Task, Fs2Streams[Task]] => Task[Assertion]
  ): Unit =
    test(testName) {
      runtime.unsafeRunToFuture {
        {
          for {
            switch <- fs2.Stream.eval(Deferred[Task, Unit])
            topic  <- fs2.Stream.eval(ZHub.bounded[Event](10)) //(InitEvent))
            subs <- fs2.Stream.eval(
                      subcriptions(
                        topic,
                        switch,
                        accountsRepository,
                        transactionsRepository
                      )
                    )
            r <- fs2.Stream.eval(
                   Resource
                     .make(
                       Task(
                         createBackend(
                           topic,
                           eventStore,
                           accountsRepository,
                           transactionsRepository
                         )
                       )
                     )(_.close())
                     .use(f)(implicitly)
                 ) concurrently subs
            _ <- fs2.Stream.eval(switch.complete(())).delayBy(1.second)
          } yield r
        }.compile.last.map(_.getOrElse(fail("no assertion")))
      }
    }
}

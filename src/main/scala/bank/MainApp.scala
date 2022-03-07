package bank

import bank.model.events.Event
import bank.routes.BankApi
import bank.services._
import bank.storage._
import zio._
import zio.interop.catz._
import org.http4s.blaze.server.BlazeServerBuilder
import zio.interop.catz.implicits._

import scala.concurrent.ExecutionContext

object MainApp extends ZIOAppDefault {

  override def run: URIO[ZEnv, ExitCode] = {
    val eventStore             = new InMemoryEventStore
    val transactionsRepository = new InMemoryTransactionsRepository
    val accountsRepository     = new InMemoryAccountsRepository

    def bankApi(topic: Hub[Event]) =
      new BankApi(
        new AccountService(eventStore, topic),
        new ClientService(eventStore),
        accountsRepository,
        transactionsRepository
      )

    for {
      topic <- ZHub.bounded[Event](10)
      subscriptions <- Listeners.subscribeListeners(
                         topic,
                         accountsRepository,
                         transactionsRepository
                       )
      api = bankApi(topic)
      _ <- (
               subscriptions concurrently BlazeServerBuilder[Task]
                 .withExecutionContext(
                   ExecutionContext.global
                 )
                 .bindHttp(8080, "localhost")
                 .withHttpApp(api.httpApp)
                 .serve
           ).compile.drain
    } yield ()
  }.either.map {
    case Left(_)  => ExitCode.failure
    case Right(_) => ExitCode.success
  }

}

package bank

import bank.model.events.Event
import bank.routes.{BankApp, BankRoutes}
import bank.services._
import bank.storage._
import cats.effect._
import fs2.concurrent.Topic
import org.http4s.blaze.server.BlazeServerBuilder
import scala.concurrent.ExecutionContext

object MainApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val eventStore             = new InMemoryEventStore[IO]
    val transactionsRepository = new InMemoryTransactionsRepository[IO]
    val accountsRepository     = new InMemoryAccountsRepository[IO]

    def bankRoutes(topic: Topic[IO, Event]) =
      new BankApp[IO](
        new BankRoutes[IO](
          new AccountService[IO](eventStore, topic),
          new ClientService[IO](eventStore),
          accountsRepository,
          transactionsRepository
        ).routes
      )

    for {
      topic <- Topic[IO, Event]
      subscriptions = Listeners.subscribeListeners(
                        topic,
                        accountsRepository,
                        transactionsRepository
                      )
      _ <- (
               subscriptions concurrently BlazeServerBuilder[IO]
                 .withExecutionContext(
                   ExecutionContext.global
                 )
                 .bindHttp(8080, "localhost")
                 .withHttpApp(bankRoutes(topic).router)
                 .serve
           ).compile.drain
    } yield ()
  }.as(ExitCode.Success)

}

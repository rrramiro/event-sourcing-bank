package bank

import bank.model.events.{Event, InitEvent}
import bank.routes.{BankApp, BankRoutes}
import bank.services._
import bank.storage._
import cats.effect._
import com.comcast.ip4s._
import fs2.concurrent.Topic
import org.http4s.ember.server.EmberServerBuilder

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
      _ <- Listeners
             .subscribeListeners(topic, accountsRepository, transactionsRepository)
             .use { subscriptions =>
               topic.publish1(InitEvent) *>
                 subscriptions
                   .concurrently(
                     fs2.Stream.eval(
                       EmberServerBuilder
                         .default[IO]
                         .withHost(ipv4"0.0.0.0")
                         .withPort(port"8212")
                         .withHttpApp(bankRoutes(topic).router)
                         .build
                         .use(_ => IO.never)
                     )
                   )
                   .compile
                   .drain
             }
    } yield ()
  }.as(ExitCode.Success)

}

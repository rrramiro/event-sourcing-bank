package bank

import bank.model.aggregates.{AccountState, ClientState}
import bank.model.events.Event
import bank.routes.{BankApp, BankRoutes}
import bank.services._
import bank.storage._
import cats.effect._
import com.comcast.ip4s._
import fs2.concurrent.Topic
import org.http4s.ember.server.EmberServerBuilder

object MainApp extends IOApp {
  // Every 5th committed event triggers a snapshot - see AccountService/ClientService.maybeSnapshot.
  private val snapshotEvery = 5

  override def run(args: List[String]): IO[ExitCode] = {
    val eventStore             = new InMemoryEventStore[IO]
    val transactionsRepository = new InMemoryTransactionsRepository[IO]
    val accountsRepository     = new InMemoryAccountsRepository[IO]
    val accountSnapshotStore   = new InMemorySnapshotStore[IO, AccountState]
    val clientSnapshotStore    = new InMemorySnapshotStore[IO, ClientState]

    def bankRoutes(topic: Topic[IO, Event]) =
      new BankApp[IO](
        new BankRoutes[IO](
          new AccountService[IO](eventStore, topic, accountSnapshotStore, snapshotEvery),
          new ClientService[IO](eventStore, topic, clientSnapshotStore, snapshotEvery),
          accountsRepository,
          transactionsRepository
        ).routes
      )

    for {
      topic <- Topic[IO, Event]
      _ <- Listeners
             .subscribeListeners(topic, accountsRepository, transactionsRepository)
             .use { subscriptions =>
               Listeners.rebuildProjections(eventStore, accountsRepository, transactionsRepository) *>
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

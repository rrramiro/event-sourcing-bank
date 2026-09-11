package bank

import java.util.UUID

import bank.model.aggregates.AccountState
import bank.model.commands._
import bank.model.events.Event
import bank.services.AccountService
import bank.storage._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.concurrent.Topic
import org.scalatest.funsuite.AnyFunSuite

class RebuildProjectionsTest extends AnyFunSuite {
  test("projections can be rebuilt from the event store alone, without the live topic") {
    val eventStore    = new InMemoryEventStore[IO]
    val snapshotStore = new InMemorySnapshotStore[IO, AccountState]
    val clientId      = UUID.randomUUID()

    val accountId = (for {
      topic <- Topic[IO, Event]
      service = new AccountService[IO](eventStore, topic, snapshotStore, snapshotEvery = 5)
      opened <- service.process(OpenAccountCommand(clientId)).value
      account = opened.getOrElse(fail("open failed"))
      _ <- service.process(DepositAccountCommand(account.aggregateId.id, 50)).value
      _ <- service.process(WithdrawAccountCommand(account.aggregateId.id, 20)).value
    } yield account.aggregateId.id).unsafeRunSync()

    // Fresh, empty repositories - nothing has ever come through a live topic subscription for them.
    val accountsRepository     = new InMemoryAccountsRepository[IO]
    val transactionsRepository = new InMemoryTransactionsRepository[IO]

    Listeners.rebuildProjections[IO](eventStore, accountsRepository, transactionsRepository).unsafeRunSync()

    val accounts     = accountsRepository.getAccounts(clientId).unsafeRunSync()
    val transactions = transactionsRepository.listByAccount(accountId).unsafeRunSync()

    assert(accounts.map(_.balance) == List(BigDecimal(30)))
    assert(transactions.map(_.amount) == List(BigDecimal(50), BigDecimal(20)))
  }
}

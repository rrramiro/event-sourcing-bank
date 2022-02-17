package bank.services

import java.util.UUID
import bank.model.aggregates.{Account, AggregateError, AggregateUnexpectedError}
import bank.model.commands._
import bank.model.BankError
import bank.model.events.Event
import bank.storage.{EvenStoreError, EventStore}
import zio.{Hub, IO, ZIO}

class AccountService(
  eventStore: EventStore,
  eventsTopic: Hub[Event]
) {

  def load(id: UUID): IO[AggregateError, Account] =
    eventStore.load(id) flatMap Account.load(id)

  def process(command: Command): IO[BankError, Account] =
    command match {
      case OpenAccountCommand(clientId) =>
        Account.open(UUID.randomUUID(), clientId) flatMap storeAndPublishEvents
      case WithdrawAccountCommand(id, amount) =>
        loadProcessStorePublish(id)(Account.withdrawn(amount))
      case DepositAccountCommand(id, amount) =>
        loadProcessStorePublish(id)(Account.deposit(amount))
      case _ => IO.fail(AggregateUnexpectedError)
    }

  private def loadProcessStorePublish(
    id: UUID
  )(f: Account => IO[AggregateError, Account]): IO[BankError, Account] =
    load(id) flatMap f flatMap storeAndPublishEvents

  private def storeAndPublishEvents(account: Account): IO[EvenStoreError, Account] =
    eventStore.store(account.aggregateId) *> ZIO
      .collectAll(
        account.aggregateId.newEvents.map(eventsTopic.publish)
      )
      .as(account)
}

package bank.services

import java.util.UUID
import bank.model.aggregates.{Account, AggregateError, AggregateUnexpectedError}
import bank.model.commands._
import bank.model.events.Event
import bank.storage.EventStore
import zio.{Hub, IO, Task, ZIO}

class AccountService(
  eventStore: EventStore,
  eventsTopic: Hub[Event]
) {

  def load(id: UUID): Task[Account] =
    eventStore.load(id) flatMap Account.load(id)

  def process(command: Command): Task[Account] =
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
  )(f: Account => IO[AggregateError, Account]): Task[Account] =
    load(id) flatMap f flatMap storeAndPublishEvents

  private def storeAndPublishEvents(account: Account): Task[Account] =
    eventStore.store(account.aggregateId) *> ZIO
      .collectAll(
        account.aggregateId.newEvents.map(eventsTopic.publish)
      )
      .as(account)
}

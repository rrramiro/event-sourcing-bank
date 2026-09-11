package bank.services

import java.util.UUID

import bank.model.aggregates.{Account, AccountState}
import bank.model.commands._
import bank.model.events.Event
import bank.storage.{EventStore, SnapshotStore}
import cats.effect._
import cats.syntax.flatMap._
import fs2.concurrent.Topic

class AccountService[F[_]: Concurrent](
  eventStore: EventStore[F],
  eventsTopic: Topic[F, Event],
  snapshotStore: SnapshotStore[F, AccountState],
  snapshotEvery: Int
) extends EventSourcedService[F, AccountState, Account](
    eventStore,
    eventsTopic,
    snapshotStore,
    snapshotEvery,
    Account
  ) {

  def process(command: AccountCommand): ResultT[Account] =
    command match {
      case OpenAccountCommand(clientId) =>
        Account.open[ResultT](UUID.randomUUID(), clientId) >>= commit
      case WithdrawAccountCommand(id, amount) =>
        loadProcessCommit(id)(Account.withdrawn[ResultT](amount))
      case DepositAccountCommand(id, amount) =>
        loadProcessCommit(id)(Account.deposit[ResultT](amount))
    }
}

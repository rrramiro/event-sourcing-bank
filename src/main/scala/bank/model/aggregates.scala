package bank.model

import java.time.ZonedDateTime
import java.util.UUID
import bank.model.events._
import cats.syntax.eq._
import cats.instances.int._
import zio.IO

object aggregates {

  sealed trait AggregateError          extends BankError
  case object AggregateNotFound        extends Exception with AggregateError
  case object AggregateVersionError    extends Exception with AggregateError
  case object AggregateUnexpectedError extends Exception with AggregateError

  trait Aggregate[State] {
    def state: State
    def aggregateId: AggregateId
  }

  final case class AggregateId(
    id: UUID,
    baseVersion: Int,
    newEvents: List[Event]
  ) {
    def nextVersion: Int = baseVersion + newEvents.size + 1
  }

  trait AggregateCompanion[State, Agg <: Aggregate[State]] {

    def apply(state: State, aggregateId: AggregateId): Agg

    def applyEvent(
      accountState: IO[AggregateError, State],
      event: Event
    ): IO[AggregateError, State]

    def load(
      id: UUID
    )(eventStream: List[Event]): IO[AggregateError, Agg] =
      eventStream
        .foldLeft(IO.fail(AggregateNotFound): IO[AggregateError, (State, Int)]) {
          case (s, e) => applyEvent(s.map(_._1), e).map(_ -> e.eventId.version)
        }
        .map {
          case (accountState, baseVersion) =>
            apply(
              state = accountState,
              aggregateId = AggregateId(
                id = id,
                baseVersion = baseVersion,
                newEvents = List.empty
              )
            )
        }

    def applyNewEvent(agg: Agg, event: Event): IO[AggregateError, Agg] =
      if (event.eventId.version === agg.aggregateId.nextVersion)
        applyEvent(IO.succeed(agg.state), event).map { s =>
          apply(
            s,
            agg.aggregateId.copy(newEvents =
              agg.aggregateId.newEvents :+ event
            )
          )
        }
      else IO.fail(AggregateVersionError)
  }

  final case class AccountState(balance: BigDecimal, clientId: UUID)

  final case class Account(
    state: AccountState,
    aggregateId: AggregateId
  ) extends Aggregate[AccountState]

  object Account extends AggregateCompanion[AccountState, Account] {

    def open(id: UUID, clientId: UUID): IO[AggregateError, Account] =
      applyNewEvent(
        Account(
          AccountState(BigDecimal(0), clientId),
          AggregateId(id, 0, List.empty)
        ),
        AccountOpenedEvent(
          clientId,
          BigDecimal(0),
          EventId(1, id, ZonedDateTime.now())
        )
      )

    def withdrawn(
      amount: BigDecimal
    )(account: Account): IO[AggregateError, Account] =
      applyNewEvent(
        account,
        AccountWithdrawnEvent(
          amount,
          account.state.balance - amount,
          EventId(
            account.aggregateId.nextVersion,
            account.aggregateId.id,
            ZonedDateTime.now()
          )
        )
      )

    def deposit(
      amount: BigDecimal
    )(account: Account): IO[AggregateError, Account] =
      applyNewEvent(
        account,
        AccountDepositedEvent(
          amount,
          account.state.balance + amount,
          EventId(
            account.aggregateId.nextVersion,
            account.aggregateId.id,
            ZonedDateTime.now()
          )
        )
      )

    def applyEvent(accountState: IO[AggregateError, AccountState], event: Event): IO[AggregateError, AccountState] =
      event match {
        case AccountOpenedEvent(clientId, balance, _) =>
          IO.succeed(AccountState(clientId = clientId, balance = balance))
        case AccountDepositedEvent(_, balance, _) =>
          accountState.map(_.copy(balance = balance))
        case AccountWithdrawnEvent(_, balance, _) =>
          //TODO NonSufficientFundsException
          accountState.map(_.copy(balance = balance))
        case _ => accountState
      }
  }

  final case class ClientState(name: String, email: Email)

  final case class Client(
    state: ClientState,
    aggregateId: AggregateId
  ) extends Aggregate[ClientState]

  object Client extends AggregateCompanion[ClientState, Client] {
    def enroll(
      id: UUID,
      name: String,
      email: Email
    ): IO[AggregateError, Client] =
      applyNewEvent(
        Client(ClientState(name, email), AggregateId(id, 0, List.empty)),
        ClientEnrolledEvent(name, email, EventId(1, id, ZonedDateTime.now()))
      )

    def update(name: String, email: Email)(
      client: Client
    ): IO[AggregateError, Client] =
      applyNewEvent(
        client,
        ClientUpdatedEvent(
          name,
          email,
          EventId(
            client.aggregateId.nextVersion,
            client.aggregateId.id,
            ZonedDateTime.now()
          )
        )
      )

    def applyEvent(
      accountState: IO[AggregateError, ClientState],
      event: Event
    ): IO[AggregateError, ClientState] =
      event match {
        case ClientEnrolledEvent(name, email, _) =>
          IO.succeed(ClientState(name, email))
        case ClientUpdatedEvent(name, email, _) =>
          accountState.map(_.copy(name = name, email = email))
        case _ => accountState
      }
  }
}

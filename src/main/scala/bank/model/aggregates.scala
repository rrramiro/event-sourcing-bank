package bank.model

import java.time.ZonedDateTime
import java.util.UUID

import bank.model.events._
import cats.Applicative
import cats.syntax.applicative._
import cats.syntax.eq._
import cats.syntax.functor._
import cats.instances.int._
import cats.mtl._

object aggregates {

  sealed trait AggregateError       extends Throwable
  case object AggregateNotFound     extends Exception with AggregateError
  case object AggregateVersionError extends Exception with AggregateError

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

    def applyEvent[F[_]: Applicative](
      accountState: F[State],
      event: Event
    ): F[State]

    def load[F[_]: Applicative](
      id: UUID
    )(eventStream: List[Event])(implicit F: FunctorRaise[F, AggregateError]): F[Agg] =
      eventStream
        .foldLeft(F.raise[(State, Int)](AggregateNotFound)) {
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

    def applyNewEvent[F[_]: Applicative](agg: Agg, event: Event)(implicit F: FunctorRaise[F, AggregateError]): F[Agg] =
      if (event.eventId.version === agg.aggregateId.nextVersion)
        applyEvent(agg.state.pure[F], event).map { s =>
          apply(
            s,
            agg.aggregateId.copy(newEvents =
              agg.aggregateId.newEvents :+ event
            )
          )
        }
      else F.raise(AggregateVersionError)
  }

  final case class AccountState(balance: BigDecimal, clientId: UUID)

  final case class Account(
    state: AccountState,
    aggregateId: AggregateId
  ) extends Aggregate[AccountState]

  object Account extends AggregateCompanion[AccountState, Account] {

    def open[F[_]: Applicative](id: UUID, clientId: UUID)(implicit F: FunctorRaise[F, AggregateError]): F[Account] =
      applyNewEvent[F](
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

    def withdrawn[F[_]: Applicative](
      amount: BigDecimal
    )(account: Account)(implicit F: FunctorRaise[F, AggregateError]): F[Account] =
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

    def deposit[F[_]: Applicative](
      amount: BigDecimal
    )(account: Account)(implicit F: FunctorRaise[F, AggregateError]): F[Account] =
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

    def applyEvent[F[_]: Applicative](accountState: F[AccountState], event: Event): F[AccountState] =
      event match {
        case AccountOpenedEvent(clientId, balance, _) =>
          AccountState(clientId = clientId, balance = balance).pure
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
    def enroll[F[_]: Applicative](
      id: UUID,
      name: String,
      email: Email
    )(implicit F: FunctorRaise[F, AggregateError]): F[Client] =
      applyNewEvent(
        Client(ClientState(name, email), AggregateId(id, 0, List.empty)),
        ClientEnrolledEvent(name, email, EventId(1, id, ZonedDateTime.now()))
      )

    def update[F[_]: Applicative](name: String, email: Email)(
      client: Client
    )(implicit F: FunctorRaise[F, AggregateError]): F[Client] =
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

    def applyEvent[F[_]: Applicative](
      accountState: F[ClientState],
      event: Event
    ): F[ClientState] =
      event match {
        case ClientEnrolledEvent(name, email, _) =>
          ClientState(name, email).pure
        case ClientUpdatedEvent(name, email, _) =>
          accountState.map(_.copy(name = name, email = email))
        case _ => accountState
      }
  }
}

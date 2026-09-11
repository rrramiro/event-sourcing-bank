package bank.model

import java.time.ZonedDateTime
import java.util.UUID

object events {

  // sealed so matches on Event's two branches (AccountEvent/ClientEvent) can be checked for
  // exhaustiveness by the compiler - see AccountEvent/ClientEvent below.
  sealed trait Event {
    def eventId: EventId
  }

  final case class EventId(
    version: Int,
    aggregateId: UUID,
    timestamp: ZonedDateTime
  )

  // Splitting Event into per-aggregate ADTs lets `applyEvent`/the listeners match exhaustively on
  // just their own aggregate's events - a new AccountEvent subtype added without updating every
  // match on AccountEvent becomes a compile error (-Xfatal-warnings), not a silently-ignored event.
  sealed trait AccountEvent extends Event

  final case class AccountDepositedEvent(
    amount: BigDecimal,
    eventId: EventId
  ) extends AccountEvent

  final case class AccountOpenedEvent(
    clientId: UUID,
    balance: BigDecimal,
    eventId: EventId
  ) extends AccountEvent

  final case class AccountWithdrawnEvent(
    amount: BigDecimal,
    eventId: EventId
  ) extends AccountEvent

  sealed trait ClientEvent extends Event

  final case class ClientEnrolledEvent(
    name: String,
    email: Email,
    eventId: EventId
  ) extends ClientEvent

  final case class ClientUpdatedEvent(
    name: String,
    email: Email,
    eventId: EventId
  ) extends ClientEvent
}

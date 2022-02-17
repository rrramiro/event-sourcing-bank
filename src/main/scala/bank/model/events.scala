package bank.model

import java.time.ZonedDateTime
import java.util.UUID

object events {

  trait Event {
    def eventId: EventId
  }

  final case class EventId(
    version: Int,
    aggregateId: UUID,
    timestamp: ZonedDateTime
  )
/*
  case object InitEvent extends Event { //TODO remove ?
    override def eventId: EventId = EventId(-1, UUID.randomUUID(), ZonedDateTime.now())
  }
*/
  final case class AccountDepositedEvent(
    amount: BigDecimal,
    balance: BigDecimal,
    eventId: EventId
  ) extends Event

  final case class AccountOpenedEvent(
    clientId: UUID,
    balance: BigDecimal,
    eventId: EventId
  ) extends Event

  final case class AccountWithdrawnEvent(
    amount: BigDecimal,
    balance: BigDecimal,
    eventId: EventId
  ) extends Event

  final case class ClientEnrolledEvent(
    name: String,
    email: Email,
    eventId: EventId
  ) extends Event

  final case class ClientUpdatedEvent(
    name: String,
    email: Email,
    eventId: EventId
  ) extends Event
}

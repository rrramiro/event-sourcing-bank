package bank.model

import java.util.UUID

object commands {

  sealed trait Command
  sealed trait AccountCommand
  sealed trait ClientCommand
  final case class OpenAccountCommand(clientId: UUID)                   extends AccountCommand
  final case class DepositAccountCommand(id: UUID, amount: BigDecimal)  extends AccountCommand
  final case class WithdrawAccountCommand(id: UUID, amount: BigDecimal) extends AccountCommand

  final case class EnrollClientCommand(name: String, email: Email)           extends ClientCommand
  final case class UpdateClientCommand(id: UUID, name: String, email: Email) extends ClientCommand
}

package bank.model

import java.util.UUID

object commands {

  trait Command
  final case class OpenAccountCommand(clientId: UUID)                   extends Command
  final case class DepositAccountCommand(id: UUID, amount: BigDecimal)  extends Command
  final case class WithdrawAccountCommand(id: UUID, amount: BigDecimal) extends Command

  final case class EnrollClientCommand(name: String, email: Email)           extends Command
  final case class UpdateClientCommand(id: UUID, name: String, email: Email) extends Command
}

package bank.routes

import bank.model.aggregates.{Account, Client}
import bank.model.commands.{
  DepositAccountCommand,
  EnrollClientCommand,
  OpenAccountCommand,
  UpdateClientCommand,
  WithdrawAccountCommand
}
import bank.model.dto.{AccountDto, ClientDto, DepositDto, WithdrawalDto}

import java.util.UUID

trait DtoCmdHelpers {

  implicit class AccountOps(account: Account) {
    def toDto: AccountDto =
      AccountDto(
        account.aggregateId.id,
        account.state.balance,
        account.state.clientId
      )
  }

  implicit class ClientOps(client: Client) {
    def toDto: ClientDto =
      ClientDto(
        client.aggregateId.id,
        client.state.name,
        client.state.email
      )
  }

  implicit class AccountDtoOps(dto: AccountDto) {
    def toCmd: OpenAccountCommand = OpenAccountCommand(dto.clientId)
  }

  implicit class DepositDtoOps(dto: DepositDto) {
    def toCmd(uuid: UUID): DepositAccountCommand = DepositAccountCommand(uuid, dto.amount)
  }

  implicit class WithdrawalDtoOps(dto: WithdrawalDto) {
    def toCmd(uuid: UUID): WithdrawAccountCommand = WithdrawAccountCommand(uuid, dto.amount)
  }

  implicit class ClientDtoOps(dto: ClientDto) {
    def enrollCmd: EnrollClientCommand             = EnrollClientCommand(dto.name, dto.email)
    def updateCmd(uuid: UUID): UpdateClientCommand = UpdateClientCommand(uuid, dto.name, dto.email)
  }

}

package bank

import java.util.UUID

import bank.model.Email
import bank.model.dto._
import bank.model.projection.TransactionProjection
import io.circe.generic.auto._
import io.circe.syntax._
import sttp.client._
import sttp.client.circe._
import eu.timepit.refined.auto._
import org.scalatest.funsuite.AsyncFunSuite

class AccountTest extends AsyncFunSuite with BankFixture {
  testApp("open") { implicit backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJson[ClientDto])
               .call
      account = AccountDto(UUID.randomUUID(), 0, dto.id)
      actual <- basicRequest
                  .post(uri"http://localhost/api/accounts")
                  .body(account.asJson.toString())
                  .response(asJson[AccountDto])
                  .call
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.balance == (0: BigDecimal))
      assert(actual.clientId == dto.id)
    }
  }

  testApp("deposit") { implicit backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJson[ClientDto])
               .call
      account = AccountDto(UUID.randomUUID(), 0, dto.id)
      initial <- basicRequest
                   .post(uri"http://localhost/api/accounts")
                   .body(account.asJson.toString())
                   .response(asJson[AccountDto])
                   .call
      deposit = DepositDto(initial.id, 10)
      actual <- basicRequest
                  .post(uri"http://localhost/api/accounts/${initial.id}/deposits")
                  .body(deposit.asJson.toString())
                  .response(asJson[AccountDto])
                  .call
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.balance == deposit.amount)
      assert(actual.clientId == dto.id)
    }
  }

  testApp("withdraw") { implicit backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJson[ClientDto])
               .call
      account = AccountDto(UUID.randomUUID(), 0, dto.id)
      initial <- basicRequest
                   .post(uri"http://localhost/api/accounts")
                   .body(account.asJson.toString())
                   .response(asJson[AccountDto])
                   .call
      deposit = DepositDto(initial.id, 15)
      _ <- basicRequest
             .post(uri"http://localhost/api/accounts/${initial.id}/deposits")
             .body(deposit.asJson.toString())
             .response(asJson[AccountDto])
             .call
      withdrawal = DepositDto(initial.id, 5)
      actual <- basicRequest
                  .post(uri"http://localhost/api/accounts/${initial.id}/withdrawals")
                  .body(withdrawal.asJson.toString())
                  .response(asJson[AccountDto])
                  .call
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.balance == (deposit.amount - withdrawal.amount))
      assert(actual.clientId == dto.id)
    }
  }

  testApp("transactions") { implicit backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJson[ClientDto])
               .call
      account = AccountDto(UUID.randomUUID(), 0, dto.id)
      initial <- basicRequest
                   .post(uri"http://localhost/api/accounts")
                   .body(account.asJson.toString())
                   .response(asJson[AccountDto])
                   .call
      deposit = DepositDto(initial.id, 15)
      _ <- basicRequest
             .post(uri"http://localhost/api/accounts/${initial.id}/deposits")
             .body(deposit.asJson.toString())
             .response(asJson[AccountDto])
             .call
      withdrawal = DepositDto(initial.id, 5)
      _ <- basicRequest
             .post(uri"http://localhost/api/accounts/${initial.id}/withdrawals")
             .body(withdrawal.asJson.toString())
             .response(asJson[AccountDto])
             .call
      actual <- basicRequest
                  .get(uri"http://localhost/api/accounts/${initial.id}/transactions")
                  .response(asJson[List[TransactionProjection]])
                  .call
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.size == 2)
    }
  }

}

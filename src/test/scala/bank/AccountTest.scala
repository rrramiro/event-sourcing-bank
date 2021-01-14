package bank

import java.util.UUID
import bank.model.Email
import bank.model.dto._
import bank.model.projection.TransactionProjection
import io.circe.generic.auto._
import io.circe.syntax._
import sttp.client3._
import eu.timepit.refined.auto._
import org.scalatest.funsuite.AsyncFunSuite

class AccountTest extends AsyncFunSuite with BankFixture {
  testApp("open") { backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJsonOrFail[ClientDto])
               .send(backend)
      account = AccountDto(UUID.randomUUID(), 0, dto.body.id)
      actual <- basicRequest
                  .post(uri"http://localhost/api/accounts")
                  .body(account.asJson.toString())
                  .response(asJsonOrFail[AccountDto])
                  .send(backend)
    } yield {
      assert(dto.body.name == client.name)
      assert(dto.body.email == client.email)
      assert(actual.body.balance == (0: BigDecimal))
      assert(actual.body.clientId == dto.body.id)
    }
  }

  testApp("deposit") { backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJsonOrFail[ClientDto])
               .send(backend)
      account = AccountDto(UUID.randomUUID(), 0, dto.body.id)
      initial <- basicRequest
                   .post(uri"http://localhost/api/accounts")
                   .body(account.asJson.toString())
                   .response(asJsonOrFail[AccountDto])
                   .send(backend)
      deposit = DepositDto(initial.body.id, 10)
      actual <- basicRequest
                  .post(uri"http://localhost/api/accounts/${initial.body.id}/deposits")
                  .body(deposit.asJson.toString())
                  .response(asJsonOrFail[AccountDto])
                  .send(backend)
    } yield {
      assert(dto.body.name == client.name)
      assert(dto.body.email == client.email)
      assert(actual.body.balance == deposit.amount)
      assert(actual.body.clientId == dto.body.id)
    }
  }

  testApp("withdraw") { backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJsonOrFail[ClientDto])
               .send(backend)
      account = AccountDto(UUID.randomUUID(), 0, dto.body.id)
      initial <- basicRequest
                   .post(uri"http://localhost/api/accounts")
                   .body(account.asJson.toString())
                   .response(asJsonOrFail[AccountDto])
                   .send(backend)
      deposit = DepositDto(initial.body.id, 15)
      _ <- basicRequest
             .post(uri"http://localhost/api/accounts/${initial.body.id}/deposits")
             .body(deposit.asJson.toString())
             .response(asJsonOrFail[AccountDto])
             .send(backend)
      withdrawal = DepositDto(initial.body.id, 5)
      actual <- basicRequest
                  .post(uri"http://localhost/api/accounts/${initial.body.id}/withdrawals")
                  .body(withdrawal.asJson.toString())
                  .response(asJsonOrFail[AccountDto])
                  .send(backend)
    } yield {
      assert(dto.body.name == client.name)
      assert(dto.body.email == client.email)
      assert(actual.body.balance == (deposit.amount - withdrawal.amount))
      assert(actual.body.clientId == dto.body.id)
    }
  }

  testApp("transactions") { backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJsonOrFail[ClientDto])
               .send(backend)
      account = AccountDto(UUID.randomUUID(), 0, dto.body.id)
      initial <- basicRequest
                   .post(uri"http://localhost/api/accounts")
                   .body(account.asJson.toString())
                   .response(asJsonOrFail[AccountDto])
                   .send(backend)
      deposit = DepositDto(initial.body.id, 15)
      _ <- basicRequest
             .post(uri"http://localhost/api/accounts/${initial.body.id}/deposits")
             .body(deposit.asJson.toString())
             .response(asJsonOrFail[AccountDto])
             .send(backend)
      withdrawal = DepositDto(initial.body.id, 5)
      _ <- basicRequest
             .post(uri"http://localhost/api/accounts/${initial.body.id}/withdrawals")
             .body(withdrawal.asJson.toString())
             .response(asJsonOrFail[AccountDto])
             .send(backend)
      actual <- basicRequest
                  .get(uri"http://localhost/api/accounts/${initial.body.id}/transactions")
                  .response(asJsonOrFail[List[TransactionProjection]])
                  .send(backend)
    } yield {
      assert(dto.body.name == client.name)
      assert(dto.body.email == client.email)
      assert(actual.body.size == 2)
    }
  }

}

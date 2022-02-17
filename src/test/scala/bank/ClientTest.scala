package bank

import java.util.UUID

import bank.model.Email
import bank.model.dto._
import bank.model.projection.AccountProjection
import io.circe.generic.auto._
import io.circe.syntax._
import sttp.client3._
import sttp.client3.circe._
import eu.timepit.refined.auto._
import org.scalatest.funsuite.AsyncFunSuite

class ClientTest extends AsyncFunSuite with BankFixture {

  testApp("enroll") { implicit backend =>
    val client = ClientDto(UUID.randomUUID(), "Jhon Doe", Email("doe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJson[ClientDto])
               .call
      actual <- basicRequest
                  .get(uri"http://localhost/api/clients/${dto.id}")
                  .response(asJson[ClientDto])
                  .call
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.name == client.name)
      assert(actual.email == client.email)
    }
  }

  testApp("update") { implicit backend =>
    val client = ClientDto(UUID.randomUUID(), "John Doe", Email("john@doe.com"))
    val clientUpdated =
      ClientDto(UUID.randomUUID(), "Jane Doe", Email("jane@doe.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJson[ClientDto])
               .call
      updated <- basicRequest
                   .put(uri"http://localhost/api/clients/${dto.id}")
                   .body(clientUpdated.asJson.toString())
                   .response(asJson[ClientDto])
                   .call
      actual <- basicRequest
                  .get(uri"http://localhost/api/clients/${dto.id}")
                  .response(asJson[ClientDto])
                  .call
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.name == clientUpdated.name)
      assert(updated.email == clientUpdated.email)
      assert(updated.name == clientUpdated.name)
      assert(actual.email == clientUpdated.email)
    }
  }

  testApp("accounts") { implicit backend =>
    val client = ClientDto(UUID.randomUUID(), "Jhon Doe", Email("doe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJson[ClientDto])
               .call
      account = AccountDto(UUID.randomUUID(), 0, dto.id)
      _ <- basicRequest
             .post(uri"http://localhost/api/accounts")
             .body(account.asJson.toString())
             .response(asJson[AccountDto])
             .call
      actual <- basicRequest
                  .get(uri"http://localhost/api/clients/${dto.id}/accounts")
                  .response(asJson[List[AccountProjection]])
                  .call
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.size == 1)
    }
  }
}

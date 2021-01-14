package bank

import java.util.UUID
import bank.model.Email
import bank.model.dto._
import bank.model.projection.AccountProjection
import io.circe.generic.auto._
import io.circe.syntax._
import sttp.client3._
import eu.timepit.refined.auto._
import org.scalatest.funsuite.AsyncFunSuite

class ClientTest extends AsyncFunSuite with BankFixture {

  testApp("enroll") { backend =>
    val client = ClientDto(UUID.randomUUID(), "Jhon Doe", Email("doe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJsonOrFail[ClientDto])
               .send(backend)
      actual <- basicRequest
                  .get(uri"http://localhost/api/clients/${dto.body.id}")
                  .response(asJsonOrFail[ClientDto])
                  .send(backend)
    } yield {
      assert(dto.body.name == client.name)
      assert(dto.body.email == client.email)
      assert(actual.body.name == client.name)
      assert(actual.body.email == client.email)
    }
  }

  testApp("update") { backend =>
    val client = ClientDto(UUID.randomUUID(), "John Doe", Email("john@doe.com"))
    val clientUpdated =
      ClientDto(UUID.randomUUID(), "Jane Doe", Email("jane@doe.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJsonOrFail[ClientDto])
               .send(backend)
      updated <- basicRequest
                   .put(uri"http://localhost/api/clients/${dto.body.id}")
                   .body(clientUpdated.asJson.toString())
                   .response(asJsonOrFail[ClientDto])
                   .send(backend)
      actual <- basicRequest
                  .get(uri"http://localhost/api/clients/${dto.body.id}")
                  .response(asJsonOrFail[ClientDto])
                  .send(backend)
    } yield {
      assert(dto.body.name == client.name)
      assert(dto.body.email == client.email)
      assert(actual.body.name == clientUpdated.name)
      assert(updated.body.email == clientUpdated.email)
      assert(updated.body.name == clientUpdated.name)
      assert(actual.body.email == clientUpdated.email)
    }
  }

  testApp("accounts") { backend =>
    val client = ClientDto(UUID.randomUUID(), "Jhon Doe", Email("doe@mail.com"))
    for {
      dto <- basicRequest
               .post(uri"http://localhost/api/clients")
               .body(client.asJson.toString())
               .response(asJsonOrFail[ClientDto])
               .send(backend)
      account = AccountDto(UUID.randomUUID(), 0, dto.body.id)
      _ <- basicRequest
             .post(uri"http://localhost/api/accounts")
             .body(account.asJson.toString())
             .response(asJsonOrFail[AccountDto])
             .send(backend)
      actual <- basicRequest
                  .get(uri"http://localhost/api/clients/${dto.body.id}/accounts")
                  .response(asJsonOrFail[List[AccountProjection]])
                  .send(backend)
    } yield {
      assert(dto.body.name == client.name)
      assert(dto.body.email == client.email)
      assert(actual.body.size == 1)
    }
  }
}

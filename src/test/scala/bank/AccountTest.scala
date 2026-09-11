package bank

import java.util.UUID
import bank.model.Email
import bank.model.dto._
import bank.model.projection.TransactionProjection
import io.circe.generic.auto._
import sttp.client3._
import eu.timepit.refined.auto._
import org.scalatest.funsuite.AsyncFunSuite

class AccountTest extends AsyncFunSuite with BankFixture {
  testApp("open") { backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto    <- enrollClient(backend, client)
      actual <- openAccount(backend, dto.id)
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.balance == (0: BigDecimal))
      assert(actual.clientId == dto.id)
    }
  }

  testApp("deposit") { backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto     <- enrollClient(backend, client)
      initial <- openAccount(backend, dto.id)
      actual  <- depositInto(backend, initial.id, 10)
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.balance == (10: BigDecimal))
      assert(actual.clientId == dto.id)
    }
  }

  testApp("withdraw") { backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto     <- enrollClient(backend, client)
      initial <- openAccount(backend, dto.id)
      _       <- depositInto(backend, initial.id, 15)
      actual  <- withdrawFrom(backend, initial.id, 5)
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.balance == (10: BigDecimal))
      assert(actual.clientId == dto.id)
    }
  }

  testApp("withdraw more than balance is rejected") { backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto     <- enrollClient(backend, client)
      initial <- openAccount(backend, dto.id)
      _       <- depositInto(backend, initial.id, 10)
      status  <- withdrawalStatus(backend, initial.id, 15)
    } yield assert(status.code == 400)
  }

  testApp("transactions") { backend =>
    val client =
      ClientDto(UUID.randomUUID(), "Jhon Doe", Email("jhondoe@mail.com"))
    for {
      dto     <- enrollClient(backend, client)
      initial <- openAccount(backend, dto.id)
      _       <- depositInto(backend, initial.id, 15)
      _       <- withdrawFrom(backend, initial.id, 5)
      actual <- eventually(
                  basicRequest
                    .get(uri"http://localhost/api/accounts/${initial.id}/transactions")
                    .response(asJsonOrFail[List[TransactionProjection]])
                    .send(backend)
                )(_.body.size == 2)
    } yield {
      assert(dto.name == client.name)
      assert(dto.email == client.email)
      assert(actual.body.size == 2)
    }
  }

}

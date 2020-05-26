package bank.model

import java.time.ZonedDateTime
import java.util.UUID

import com.github.ghik.silencer.silent
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEnumerationCodec

object projection {

  final case class AccountProjection(
    accountId: UUID,
    clientId: UUID,
    balance: BigDecimal,
    version: Int
  )

  sealed trait TransactionType

  object TransactionType {
    case object Deposit    extends TransactionType
    case object Withdrawal extends TransactionType

    @silent
    private implicit val config: Configuration =
      Configuration.default.copy(transformConstructorNames = _.toUpperCase)

    @SuppressWarnings(Array("org.wartremover.warts.Equals"))
    implicit val codec: Codec[TransactionType] = deriveEnumerationCodec[TransactionType]
  }

  final case class TransactionProjection(
    accountId: UUID,
    `type`: TransactionType,
    amount: BigDecimal,
    timestamp: ZonedDateTime,
    version: Int
  )
}

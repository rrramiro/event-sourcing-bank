package bank.model

import io.circe.{Decoder, Encoder}
import io.circe.refined._

final case class Email(value: StringEmail)

object Email {
  implicit val decoder: Decoder[Email] = Decoder[StringEmail].map(Email.apply)
  implicit val encoder: Encoder[Email] = Encoder[StringEmail].contramap[Email](_.value)
}

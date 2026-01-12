package za.co.absa.atum.model.dto

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class AdditionalDataItemV2DTO(
  key: String,
  value: Option[String],
  author: String
)

object AdditionalDataItemV2DTO {
  implicit val encodeAdditionalDataItemV2DTO: Encoder[AdditionalDataItemV2DTO] = deriveEncoder
  implicit val decodeAdditionalDataItemV2DTO: Decoder[AdditionalDataItemV2DTO] = deriveDecoder
}

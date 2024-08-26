package za.co.absa.atum.model.dto

import io.circe._
import io.circe.generic.semiauto._

case class MeasureWithIdDTO (
  measureName: String,
  measuredColumns: Seq[String]
)

object MeasureWithIdDTO {
  implicit val decodeMeasureWithIdDTO: Decoder[MeasureWithIdDTO] = deriveDecoder[MeasureWithIdDTO]
  implicit val encoderMeasureWithIdDTO: Encoder[MeasureWithIdDTO] = deriveEncoder[MeasureWithIdDTO]
}

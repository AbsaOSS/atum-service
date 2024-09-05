package za.co.absa.atum.model.dto

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class CheckpointQueryDTOV2 (
  iPartitioningId: Long,
  limit: Option[Int],
  checkpointName: Option[String],
  offset: Option[Long]
)

object CheckpointQueryDTOV2 {
  implicit val decodeCheckpointQueryDTOV2: Decoder[CheckpointQueryDTOV2] = deriveDecoder
  implicit val encodeCheckpointQueryDTOV2: Encoder[CheckpointQueryDTOV2] = deriveEncoder
}

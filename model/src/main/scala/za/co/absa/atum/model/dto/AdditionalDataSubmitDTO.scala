package za.co.absa.atum.model.dto

case class AdditionalDataSubmitDTO (
  partitioning: PartitioningDTO,
  additionalData: Map[String, Option[String]]
)

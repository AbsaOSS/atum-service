package za.co.absa.atum.model.dto

case class AdditionalDataSubmitDTO (
  atumPartitioning: PartitioningDTO,
  additionalData: AdditionalDataDTO,
  author: String
)

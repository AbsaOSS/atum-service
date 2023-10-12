package za.co.absa.atum.model.dto

import za.co.absa.atum.model.Partitioning

case class PartitioningDTO (
  partitioning: Partitioning,
  parentPartitioning: Option[Partitioning]
)

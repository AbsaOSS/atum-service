package za.co.absa.atum.server.model

import za.co.absa.atum.model.dto.PartitioningDTO

case class PartitioningFromDB (
  id: Option[Long],
  partitioning: Option[PartitioningDTO],
  parentPartitioning: Option[PartitioningDTO],
  author: Option[String]
)

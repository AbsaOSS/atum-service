package za.co.absa.atum.model.types

import za.co.absa.atum.model.types.BasicTypes.{AdditionalData, AtumPartitions}

case class Checkpoint (
  id: String,
  partitioning: AtumPartitions,
  additionalData: AdditionalData,
  )

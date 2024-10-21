package za.co.absa.atum.model.types

import za.co.absa.atum.model.dto.MeasurementDTO

import java.time.ZonedDateTime

case class Checkpoint (
  id: String,
  name: String,
  author: String,
  measuredByAtumAgent: Boolean = false,
  processStartTime: ZonedDateTime,
  processEndTime: Option[ZonedDateTime],
  measurements: Set[MeasurementDTO]
)

package za.co.absa.atum.web.model

import java.util.UUID

case class ControlMeasure(
  id: Option[UUID],
  flowId: UUID,
  partitionId: UUID,
  metadata: ControlMeasureMetadata,
  runUniqueId: Option[String] = None, // todo why not UUID?
  checkpoints: List[Checkpoint] = List.empty
) extends BaseApiModel {

  override def withId(uuid: UUID): ControlMeasure = copy(id = Some(uuid))
}

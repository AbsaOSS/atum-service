package za.co.absa.atum.web.model

import java.util.UUID

case class Partition(
  id: Option[UUID],
  flowId: UUID
) extends BaseApiModel {

  override def withId(uuid: UUID): Partition = copy(id = Some(uuid))
}

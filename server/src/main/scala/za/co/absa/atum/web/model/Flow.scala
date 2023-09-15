package za.co.absa.atum.web.model

import java.util.UUID

case class Flow(
  id: Option[UUID],
  description: Option[String],
  properties: Map[String, String] = Map.empty
) extends BaseApiModel {

  def apply(desc: Option[String] = None): Flow = Flow(None, desc)

  override def withId(uuid: UUID): Flow = copy(id = Some(uuid))
}

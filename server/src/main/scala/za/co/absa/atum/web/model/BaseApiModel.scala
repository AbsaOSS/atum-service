package za.co.absa.atum.web.model

import java.util.UUID

trait BaseApiModel {
  def id: Option[UUID]

  // todo def withId[T <: BaseApiModel](uuid: UUID): T or even def withId[T <: BaseApiModel[T]](uuid: UUID): T ?
  def withId(uuid: UUID): BaseApiModel

  def entityName: String = this.getClass.getSimpleName
}

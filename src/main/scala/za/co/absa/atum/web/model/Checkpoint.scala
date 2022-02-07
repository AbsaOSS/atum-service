/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.atum.web.model

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import za.co.absa.atum.web.model.Checkpoint.CheckpointStatus

import java.util.UUID

class CheckpointStatusTypeRef extends TypeReference[CheckpointStatus.type]

case class Checkpoint(id: Option[UUID],
                      name: String,
                      software: Option[String] = None,
                      version: Option[String] = None,
                      processStartTime: String,
                      processEndTime: String,
                      workflowName: String,
                      order: Int,
                      @JsonScalaEnumeration(classOf[CheckpointStatusTypeRef]) status: CheckpointStatus.Value = CheckpointStatus.Open,
                      measurements: List[Measurement] = List.empty) extends BaseApiModel{



  override def withId(uuid: UUID): Checkpoint = copy(id = Some(uuid))

  def withUpdate(update: CheckpointUpdate): Checkpoint = {
    this
      .updateIfDefined(update.name) { case (field, cp) => cp.copy(name = field) }
      .updateIfDefined(update.software) { case (field, cp) => cp.copy(software = Some(field)) } // cannot be unset atm
      .updateIfDefined(update.version) { case (field, cp) => cp.copy(version = Some(field)) }
      .updateIfDefined(update.processStartTime) { case (field, cp) => cp.copy(processStartTime = field) }
      .updateIfDefined(update.processEndTime) { case (field, cp) => cp.copy(processEndTime = field) }
      .updateIfDefined(update.workflowName) { case (field, cp) => cp.copy(workflowName = field) }
      .updateIfDefined(update.order) { case (field, cp) => cp.copy(order = field) }
      .updateIfDefined(update.status) { case (field, cp) => cp.copy(status = field) }
  }

  def updateIfDefined[T](optField: Option[T])(updateFn: (T, Checkpoint) => Checkpoint): Checkpoint = {
    optField match {
      case None => this
      case Some(field) => updateFn(field, this)
    }
  }

}

object Checkpoint {

  object CheckpointStatus extends Enumeration {

    val Open = Value("open")
    val Closed = Value("closed")
  }
}

case class CheckpointUpdate(name: Option[String] = None,
                            software: Option[String] = None,
                            version: Option[String] = None,
                            processStartTime: Option[String] = None,
                            processEndTime: Option[String] = None,
                            workflowName: Option[String] = None,
                            order: Option[Int] = None,
                            @JsonScalaEnumeration(classOf[CheckpointStatusTypeRef]) status: Option[CheckpointStatus.Value] = None
                           )

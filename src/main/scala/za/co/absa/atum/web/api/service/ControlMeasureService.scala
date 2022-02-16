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

package za.co.absa.atum.web.api.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.atum.web.api.NotFoundException
import za.co.absa.atum.web.dao.ApiModelDao
import za.co.absa.atum.web.model._

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Service
class ControlMeasureService @Autowired()(flowService: FlowService, segmentationService: SegmentationService,
                                         dao: ApiModelDao[ControlMeasure])
  extends BaseApiService[ControlMeasure](dao) {

  override def add(cm: ControlMeasure): Future[UUID] = {
    require(cm.id.isEmpty, "A new ControlMeasure payload must not have id!")
    require(cm.checkpoints.isEmpty, "A new ControlMeasure payload must not have checkpoints! Add them separately.")

    withFlowAndSegExistF(cm) {
      super.add(cm)
    }
  }


  override def update(cm: ControlMeasure): Future[Boolean] = {
    require(cm.id.nonEmpty, "A ControlMeasure update must have its id defined!")
    val cmId = cm.id.get

    withExistingEntityF(cmId) { existingCm =>
      withFlowAndSegExistF(cm) { // checking the new CM
        assert(existingCm.id.equals(cm.id)) // just to be sure that the content matches the key
        super.update(cm)
      }
    }
  }

  def withFlowAndSegExistF[S](cm: ControlMeasure)(fn: => Future[S]): Future[S] = {
    flowService.withFlowExistsF(cm.flowId) {
      segmentationService.withSegmentationExistsF(cm.segmentationId) {
        fn
      }
    }
  }

  def updateMetadata(id: UUID, metadata: ControlMeasureMetadata): Future[Boolean] = {
    withExistingEntityF(id) { existingCm =>
      val updatedCm = existingCm.copy(metadata = metadata)
      super.update(updatedCm)
    }
  }

  def getListByFlowAndSegIds(flowId: UUID, segId: UUID, limit: Int, offset: Int): Future[List[ControlMeasure]] = {
    val filter: ControlMeasure => Boolean = {
      cm => cm.flowId.equals(flowId) && cm.segmentationId.equals(segId)
    }
    super.getList(offset, limit, filter)
  }

  // checkpoints:
  def addCheckpoint(cmId: UUID, checkpoint: Checkpoint): Future[UUID] = {
    require(checkpoint.id.isEmpty, "A new Checkpoint payload must not have id!")

    withExistingEntityF(cmId) { cm =>
      val newId = UUID.randomUUID()
      val newCheckpointWithId = checkpoint.withId(newId)
      val updatedCm = cm.copy(checkpoints = (cm.checkpoints ++ List(newCheckpointWithId)))

      super.update(updatedCm).map(_ => newId)
    }
  }

  def getCheckpointList(cmId: UUID): Future[List[Checkpoint]] = {
    withExistingEntity(cmId) {
      _.checkpoints
    }
  }

  def getCheckpointById(cmId: UUID, cpId: UUID): Future[Checkpoint] = {
    withExistingEntity(cmId) {
      _.checkpoints.find(_.id.contains(cpId)) match {
        case None => throw NotFoundException(s"Checkpoint referenced by id=$cpId was not found in ControlMeasure id=$cmId")
        case Some(cp) => cp
      }
    }
  }

  def updateCheckpoint(cmId: UUID, cpId: UUID, checkpointUpdate: CheckpointUpdate): Future[Boolean] = {

    withExistingEntityF(cmId) { existingCm =>
      existingCm.checkpoints.find(_.id.contains(cpId)) match {
        case None => throw NotFoundException(s"Checkpoint referenced by id=$cpId was not found in ControlMeasure id=$cmId")
        case Some(existingCp) =>
          val updatedCps = existingCm.checkpoints.map {
            case cp@Checkpoint(Some(`cpId`), _, _, _, _, _, _, _, _, _) => cp.withUpdate(checkpointUpdate) // reflects the update
            case cp => cp // other CPs untouched
          }

          val updatedCm = existingCm.copy(checkpoints = updatedCps)
          update(updatedCm)
      }
    }
  }

  // measurements:
  def getMeasurements(cmId: UUID, cpId: UUID): Future[List[Measurement]] = {
    getCheckpointById(cmId, cpId).map {
      _.measurements
    }
  }

  def addMeasurement(cmId: UUID, cpId: UUID, measurement: Measurement): Future[Boolean] = {
    withExistingEntityF(cmId) { existingCm =>
      existingCm.checkpoints.find(_.id.contains(cpId)) match {
        case None => throw NotFoundException(s"Checkpoint referenced by id=$cpId was not found in ControlMeasure id=$cmId")
        case Some(existingCp) =>
          val updatedCps = existingCm.checkpoints.map {
            case cp@Checkpoint(Some(`cpId`), _, _, _, _, _, _, _, _, _) => cp.copy(measurements = existingCp.measurements ++ List(measurement))
            case cp => cp // other CPs untouched
          }

          val updatedCm = existingCm.copy(checkpoints = updatedCps)
          update(updatedCm)
      }
    }
  }

  override val entityName: String = "ControlMeasure"
}



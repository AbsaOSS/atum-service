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
import za.co.absa.atum.web.model.{Checkpoint, CheckpointUpdate, Flow, FlowMetadata, Measurement}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Service
class FlowService @Autowired()(flowDefService: FlowDefinitionService, dao: ApiModelDao[Flow]) extends BaseApiService[Flow](dao) {

  override def add(flow: Flow): Future[UUID] = {
    require(flow.id.isEmpty, s"A new $entityName payload must not have id!")
    require(flow.checkpoints.isEmpty, s"A new $entityName payload must not have checkpoints! Add them separately.")

    flowDefService.withExistingEntityF(flow.flowDefId) { existingFlowDef =>
      whenSegmentationsRequirementsAreMet(flow, existingFlowDef.requiredSegmentations) {
        super.add(flow)
      }
    }
  }

  override def update(flowUpdate: Flow): Future[Boolean] = {
    require(flowUpdate.id.nonEmpty, s"A $entityName update must have its id defined!")
    val flowId = flowUpdate.id.get

    withExistingEntityF(flowId) { existingFlow =>
      flowDefService.withExistingEntityF(flowUpdate.flowDefId) { flowDef =>
        whenSegmentationsRequirementsAreMet(flowUpdate, flowDef.requiredSegmentations) { // update conforms to flowDef seg requirements
          assert(existingFlow.id.equals(flowUpdate.id)) // just to be sure that the content matches the key
          super.update(flowUpdate)
        }
      }
    }
  }

  private[service] def whenSegmentationsRequirementsAreMet[S](flow: Flow, requiredSegmentations: Set[String])(fn: => Future[S]): Future[S] = {
    flow.missingSegmentationsOf(requiredSegmentations) match {
      case missingSet if missingSet.nonEmpty =>
        Future.failed(new IllegalArgumentException(s"Required segmentation was not present in the $entityName, " +
          s"missing segmentation values for fields: ${missingSet.mkString(", ")}."))
      case _ => fn
    }
  }

  def updateMetadata(id: UUID, metadata: FlowMetadata): Future[Boolean] = {
    withExistingEntityF(id) { existingFlow =>
      val updatedFlow = existingFlow.copy(metadata = metadata)
      super.update(updatedFlow)
    }
  }

  def getListByFlowDefId(flowDefId: UUID, limit: Int, offset: Int): Future[List[Flow]] = {
    val filter: Flow => Boolean = {
      _.flowDefId.equals(flowDefId)
    }
    super.getList(limit, offset, filter)
  }

  // checkpoints:
  def addCheckpoint(flowId: UUID, checkpoint: Checkpoint): Future[UUID] = {
    require(checkpoint.id.isEmpty, "A new Checkpoint payload must not have id!")

    withExistingEntityF(flowId) { existingFlow =>
      val newId = UUID.randomUUID()
      val newCheckpointWithId = checkpoint.withId(newId)
      val updatedFlow = existingFlow.copy(checkpoints = (existingFlow.checkpoints ++ List(newCheckpointWithId)))

      super.update(updatedFlow).map(_ => newId)
    }
  }

  def getCheckpointList(flowId: UUID): Future[List[Checkpoint]] = {
    withExistingEntity(flowId) {
      _.checkpoints
    }
  }

  def getCheckpointById(flowId: UUID, cpId: UUID): Future[Checkpoint] = {
    withExistingEntity(flowId) {
      _.checkpoints.find(_.id.contains(cpId)) match {
        case None => throw NotFoundException(s"Checkpoint referenced by id=$cpId was not found in $entityName id=$flowId")
        case Some(cp) => cp
      }
    }
  }

  def updateCheckpoint(flowId: UUID, cpId: UUID, checkpointUpdate: CheckpointUpdate): Future[Boolean] = {

    withExistingEntityF(flowId) { existingFlow =>
      existingFlow.checkpoints.find(_.id.contains(cpId)) match {
        case None => throw NotFoundException(s"Checkpoint referenced by id=$cpId was not found in $entityName id=$flowId")
        case Some(existingCp) =>
          val updatedCps = existingFlow.checkpoints.map {
            case cp@Checkpoint(Some(`cpId`), _, _, _, _, _, _, _, _, _) => cp.withUpdate(checkpointUpdate) // reflects the update
            case cp => cp // other CPs untouched
          }

          val updatedFlow = existingFlow.copy(checkpoints = updatedCps)
          update(updatedFlow)
      }
    }
  }

  // measurements:
  def getMeasurements(flowId: UUID, cpId: UUID): Future[List[Measurement]] = {
    getCheckpointById(flowId, cpId).map {
      _.measurements
    }
  }

  def addMeasurement(flowId: UUID, cpId: UUID, measurement: Measurement): Future[Boolean] = {
    withExistingEntityF(flowId) { existingFlow =>
      existingFlow.checkpoints.find(_.id.contains(cpId)) match {
        case None => throw NotFoundException(s"Checkpoint referenced by id=$cpId was not found in $entityName id=$flowId")
        case Some(existingCp) =>
          val updatedCps = existingFlow.checkpoints.map {
            case cp@Checkpoint(Some(`cpId`), _, _, _, _, _, _, _, _, _) => cp.copy(measurements = existingCp.measurements ++ List(measurement))
            case cp => cp // other CPs untouched
          }

          val updatedFlow = existingFlow.copy(checkpoints = updatedCps)
          update(updatedFlow)
      }
    }
  }


  override val entityName: String = "Flow"
}



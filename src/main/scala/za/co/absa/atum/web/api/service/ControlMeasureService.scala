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
import za.co.absa.atum.web.model.{Checkpoint, CheckpointUpdate, ControlMeasure, ControlMeasureMetadata, Flow, Measurement, Segmentation}

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Service
class ControlMeasureService @Autowired()(flowService: FlowService, segmentationService: SegmentationService)
  extends BaseApiService[ControlMeasure] {

  // temporary storage // redo with db-persistence layer when ready
  val inmemory: mutable.Map[UUID, ControlMeasure] = scala.collection.mutable.Map[UUID, ControlMeasure]()

  def getList(limit: Int, offset: Int): Future[List[ControlMeasure]] = Future {
    inmemory.values.drop(offset).take(limit).toList // limiting, todo pagination or similar
  }

  def add(cm: ControlMeasure): Future[UUID] = {
    require(cm.id.isEmpty, "A new ControlMeasure payload must not have id!")
    require(cm.checkpoints.isEmpty, "A new ControlMeasure payload must not have checkpoints! Add them separately.")

    checkFlowAndSegExistAndThen(cm) {
      val newId = UUID.randomUUID()
      inmemory.put(newId, cm.withId(newId)) // assuming the persistence would throw on error
      newId
    }
  }


  def update(cm: ControlMeasure): Future[Boolean] = {
    require(cm.id.nonEmpty, "A ControlMeasure update must have its id defined!")
    val cmId = cm.id.get

    withExistingEntityF(cmId) { existingCm =>
      checkFlowAndSegExistAndThen(cm) { // checking the new CM
        assert(existingCm.id.equals(cm.id)) // just to be sure that the content matches the key
        inmemory.put(cmId, cm) match {
          case None => throw new IllegalStateException(s"Expected to find previous persisted version of ControlMeasure by id=$cmId, but found none.")
          case Some(_) => true
        }
      }
    }
  }

  def checkFlowAndSegExistAndThen[S](cm: ControlMeasure)(fn: => S): Future[S] = {
    val check: Future[Unit] = for {
      flowExists <- flowService.exists(cm.flowId)
      _ = if (!flowExists) throw NotFoundException(s"Referenced flow (flowId=${cm.flowId}) was not found.")
      segExists <- segmentationService.exists(cm.segmentationId)
      _ = if (!segExists) throw NotFoundException(s"Referenced segmentation (segId=${cm.segmentationId}) was not found.")
    } yield ()

    check.map(_ => fn)
  }

  def updateMetadata(id: UUID, metadata: ControlMeasureMetadata): Future[Unit] = {
    withExistingEntity(id) { existingCm =>
      val updatedCm = existingCm.copy(metadata = metadata)
      inmemory.put(id, updatedCm) match {
        case None => throw new IllegalStateException(s"Expected to find previous persisted version of ControlMeasure by id=$id, but found none.")
        case Some(_) => // expected
      }
    }
  }

  def getById(uuid: UUID): Future[Option[ControlMeasure]] = Future {
    inmemory.get(uuid)
  }

  def getListByFlowAndSegIds(flowId: UUID, segId: UUID, limit: Int, offset: Int): Future[List[ControlMeasure]] = Future {
    inmemory.values
      .filter(cm => cm.flowId.equals(flowId) && cm.segmentationId.equals(segId))
      .drop(offset).take(limit)
      .toList
  }

  // checkpoints:
  def addCheckpoint(cmId: UUID, checkpoint: Checkpoint): Future[UUID] = {
    require(checkpoint.id.isEmpty, "A new CP payload must not have id!")

    withExistingEntity(cmId) { cm =>
      val newId = UUID.randomUUID()
      val newCheckpointWithId = checkpoint.withId(newId)
      val updatedCm = cm.copy(checkpoints = (cm.checkpoints ++ List(newCheckpointWithId)))
      inmemory.put(cmId, updatedCm) // assuming the persistence would throw on error
      newId
    }
  }

  def getCheckpointList(cmId: UUID): Future[List[Checkpoint]] = {
    withExistingEntity(cmId) {
      _.checkpoints
    }
  }

  def getCheckpointById(cmId: UUID, cpId: UUID): Future[Checkpoint] = {
    withExistingEntity(cmId) {
      _.checkpoints.find(_.id.equals(Some(cpId))) match {
        case None => throw NotFoundException(s"Checkpoint referenced by id=$cpId was not found in ControlMeasure id=$cmId")
        case Some(cp) => cp
      }
    }
  }

  def updateCheckpoint(cmId: UUID, cpId: UUID, checkpointUpdate: CheckpointUpdate): Future[Boolean] = {

    withExistingEntityF(cmId) { existingCm =>
      existingCm.checkpoints.find(_.id.equals(Some(cpId))) match {
        case None => throw NotFoundException(s"Checkpoint referenced by id=$cpId was not found in ControlMeasure id=$cmId")
        case Some(existingCp) =>
          assert(existingCp.id.equals(Some(cpId))) // just to be sure that the content matches the key
          val updatedCps = existingCm.checkpoints.map {
            case cp @ Checkpoint(Some(`cpId`), _, _, _, _, _, _, _, _, _) => cp.withUpdate(checkpointUpdate) // reflects the update
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
      existingCm.checkpoints.find(_.id.equals(Some(cpId))) match {
        case None => throw NotFoundException(s"Checkpoint referenced by id=$cpId was not found in ControlMeasure id=$cmId")
        case Some(existingCp) =>
          assert(existingCp.id.equals(Some(cpId))) // just to be sure that the content matches the key
          val updatedCps = existingCm.checkpoints.map {
            case cp @ Checkpoint(Some(`cpId`), _, _, _, _, _, _, _, _, _) => cp.copy(measurements = existingCp.measurements ++ List(measurement))
            case cp => cp // other CPs untouched
          }

          val updatedCm = existingCm.copy(checkpoints = updatedCps)
          update(updatedCm)
      }
    }
  }

  override protected def entityName: String = "ControlMeasure"
}



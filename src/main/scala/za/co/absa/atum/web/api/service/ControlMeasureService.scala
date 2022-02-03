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
import za.co.absa.atum.web.model.{ControlMeasure, ControlMeasureMetadata, Flow, Segmentation}

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

    checkFlowAndSegExistAndThen(cm) {
      val newId = UUID.randomUUID()
      inmemory.put(newId, cm.withId(newId)) // assuming the persistence would throw on error
      newId
    }
  }


  def update(cm: ControlMeasure): Future[Unit] = {
    require(cm.id.nonEmpty, "A ControlMeasure update must have its id defined!")
    val cmId = cm.id.get

    checkFlowAndSegExistAndThen(cm) {
      inmemory.get(cmId) match {
        case None => throw NotFoundException(s"ControlMeasure referenced by id=${cmId} was not found.")
        case Some(existingCm) =>
          assert(existingCm.id.equals(cm.id)) // just to be sure that the content matches the key
          inmemory.put(cmId, cm) match {
            case None => throw new IllegalStateException(s"Expected to find previous persisted version of ControlMeasure by id=$cmId, but found none.")
            case Some(_) =>
          }
      }
    }
  }

  def checkFlowAndSegExistAndThen[S](cm: ControlMeasure)(fn: => S): Future[S] = {
    val check: Future[Unit] = for {
      flowExists <- flowService.exists(cm.flowId)
      _ = if (!flowExists) throw NotFoundException(s"Referenced flow (flowId=${cm.flowId}) was not found.")
      segExists <- segmentationService.exists(cm.segmentationId)
      _ = if (!segExists) throw NotFoundException(s"Referenced segmentations (segId=${cm.segmentationId}) was not found.")
    } yield ()

    check.map(_ => fn)
  }

  def updateMetadata(id: UUID, metadata: ControlMeasureMetadata): Future[Unit] = Future {
    inmemory.get(id) match {
      case None => throw NotFoundException(s"ControlMeasure referenced by id=${id} was not found.")
      case Some(existingCm) =>
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

}



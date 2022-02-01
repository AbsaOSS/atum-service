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
import za.co.absa.atum.web.model.{Flow, Segmentation}

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Service
class SegmentationService @Autowired()(flowService: FlowService) extends BaseApiService[Segmentation] {
  // temporary storage // redo with db-persistence layer when ready
  val inmemory: mutable.Map[UUID, Segmentation] = scala.collection.mutable.Map[UUID, Segmentation]()

  def getList(limit: Int, offset: Int): Future[List[Segmentation]] = Future {
    inmemory.values.drop(offset).take(limit).toList // limiting, todo pagination or similar
  }

  def add(seg: Segmentation): Future[UUID] = {
    require(seg.id.isEmpty)

    flowService.getById(seg.flowId).flatMap {
      case None => throw NotFoundException(s"Flow referenced by flowId=${seg.flowId} not found!")
      case Some(_) =>
        // persistence impl: supplies the ID internally:
        val newId = UUID.randomUUID()
        inmemory.put(newId, seg.withId(newId)) // assuming the persistence would throw on error
        Future.successful(newId)
    }
  }

  def getById(uuid: UUID): Future[Option[Segmentation]] = Future {
    inmemory.get(uuid)
  }

}



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

package za.co.absa.atum.server.api.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.atum.server.api.NotFoundException
import za.co.absa.atum.server.dao.ApiModelDao
import za.co.absa.atum.server.model.Partition

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



@Service
class SegmentationService @Autowired()(flowService: FlowService, dao: ApiModelDao[Partition]) extends BaseApiService[Partition](dao) {

  override def add(seg: Partition): Future[UUID] = {
    require(seg.id.isEmpty, "A new Segmentation payload must not have id!")
    flowService.withFlowExistsF(seg.flowId) {
      super.add(seg)
    }
  }

  override def update(seg: Partition): Future[Boolean] = {
    require(seg.id.nonEmpty, "Updated segmentation must have id!")
    flowService.withFlowExistsF(seg.flowId) {
      super.update(seg)
    }
  }

  def withSegmentationExistsF[S](segId: UUID)(fn: => Future[S]): Future[S] = {
    val check: Future[Unit] = for {
      segExists <- this.exists(segId)
      _ = if (!segExists) throw NotFoundException(s"Referenced segmentation (segId=$segId) was not found.")
    } yield ()

    check.flatMap(_ => fn)
  }

  override val entityName: String = "Segmentation"
}



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

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.repository.FlowRepository
import za.co.absa.atum.server.model.CheckpointFromDB
import za.co.absa.db.fadb.exceptions.StatusException
import za.co.absa.db.fadb.status
import zio._


class FlowServiceImpl(flowRepository: FlowRepository)
  extends FlowService with BaseService {

  override def getFlowCheckpoints(checkpointQueryDTO: CheckpointQueryDTO):
  IO[ServiceError, Either[StatusException, Seq[status.Row[CheckpointDTO]]]] = {
    for {
      checkpointsFromDB <- repositoryCall(
        flowRepository.getFlowCheckpoints(checkpointQueryDTO), "getFlowCheckpoints"
      ).mapError(error => ServiceError(error.message))
        .flatMap(ZIO.fromEither(_))
      checkpointDTOs <- ZIO.foreach(checkpointsFromDB) {
        checkpointFromDB  =>
          ZIO.fromEither(CheckpointFromDB.toCheckpointDTO(checkpointQueryDTO.partitioning, checkpointFromDB))
            .mapError(error => ServiceError(error.getMessage))
      }
    } yield checkpointDTOs
  }

}

object FlowServiceImpl {
  val layer: URLayer[FlowRepository, FlowService] = ZLayer {
    for {
      flowRepository <- ZIO.service[FlowRepository]
    } yield new FlowServiceImpl(flowRepository)
  }
}

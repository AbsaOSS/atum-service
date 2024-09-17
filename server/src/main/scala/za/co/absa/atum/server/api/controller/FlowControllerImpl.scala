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

package za.co.absa.atum.server.api.controller

import za.co.absa.atum.model.dto.{CheckpointDTO, CheckpointQueryDTO, CheckpointV2DTO}
import za.co.absa.atum.server.api.service.FlowService
import za.co.absa.atum.server.model.{ErrorResponse, PaginatedResult}
import za.co.absa.atum.server.model.SuccessResponse.PaginatedResponse
import za.co.absa.atum.server.model.SuccessResponse.MultiSuccessResponse
import zio._

class FlowControllerImpl(flowService: FlowService) extends FlowController with BaseController {

  override def getFlowCheckpointsV2(
    checkpointQueryDTO: CheckpointQueryDTO
  ): IO[ErrorResponse, MultiSuccessResponse[CheckpointDTO]] = {
    mapToMultiSuccessResponse(
      serviceCall[Seq[CheckpointDTO], Seq[CheckpointDTO]](
        flowService.getFlowCheckpoints(checkpointQueryDTO)
      )
    )
  }

  override def getFlowCheckpoints(
    partitioningId: Long,
    limit: Option[RuntimeFlags],
    offset: Option[Long],
    checkpointName: Option[String]
  ): IO[ErrorResponse, PaginatedResponse[CheckpointV2DTO]] = {
    val flowData = serviceCall[PaginatedResult[CheckpointV2DTO], PaginatedResult[CheckpointV2DTO]](
      flowService.getFlowCheckpointsV2(partitioningId, limit, offset, checkpointName)
    )
    mapToPaginatedResponse(limit.get, offset.get, flowData)
  }
}

object FlowControllerImpl {
  val layer: URLayer[FlowService, FlowController] = ZLayer {
    for {
      flowService <- ZIO.service[FlowService]
    } yield new FlowControllerImpl(flowService)
  }
}

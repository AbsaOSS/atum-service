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

package za.co.absa.atum.server.api.v2.controller

import za.co.absa.atum.model.dto.CheckpointWithPartitioningDTO
import za.co.absa.atum.model.envelopes.ErrorResponse
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.server.api.common.controller.BaseController
import za.co.absa.atum.server.api.v2.service.FlowService
import za.co.absa.atum.server.model.PaginatedResult
import zio._

class FlowControllerImpl(flowService: FlowService) extends FlowController with BaseController {

  // to be replaced (and moved to checkpointcontroller) with new implementation in #233
  override def getFlowCheckpoints(
    flowId: Long,
    limit: Option[Int],
    offset: Option[Long],
    checkpointName: Option[String]
  ): IO[ErrorResponse, PaginatedResponse[CheckpointWithPartitioningDTO]] = {
    val flowData =
      serviceCall[PaginatedResult[CheckpointWithPartitioningDTO], PaginatedResult[CheckpointWithPartitioningDTO]](
        flowService.getFlowCheckpoints(flowId, limit, offset, checkpointName)
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

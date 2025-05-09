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

import za.co.absa.atum.model.ApiPaths.V2Paths
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.model.envelopes.ErrorResponse
import za.co.absa.atum.model.envelopes.SuccessResponse.{PaginatedResponse, SingleSuccessResponse}
import za.co.absa.atum.server.api.common.controller.BaseController
import za.co.absa.atum.server.api.v2.service.CheckpointService
import za.co.absa.atum.server.model.PaginatedResult
import zio._

import java.util.UUID

class CheckpointControllerImpl(checkpointService: CheckpointService) extends CheckpointController with BaseController {

  override def postCheckpoint(
    partitioningId: Long,
    checkpointV2DTO: CheckpointV2DTO
  ): IO[ErrorResponse, (SingleSuccessResponse[CheckpointV2DTO], String)] = {
    for {
      response <- mapToSingleSuccessResponse(
        serviceCall[Unit, CheckpointV2DTO](
          checkpointService.saveCheckpoint(partitioningId, checkpointV2DTO),
          _ => checkpointV2DTO
        )
      )
      uri <- createRootAnchoredResourcePath(
        Seq(V2Paths.Partitionings, partitioningId.toString, V2Paths.Checkpoints, checkpointV2DTO.id.toString),
        apiVersion = 2
      )
    } yield (response, uri)
  }

  override def getPartitioningCheckpoint(
    partitioningId: Long,
    checkpointId: UUID
  ): IO[ErrorResponse, SingleSuccessResponse[CheckpointV2DTO]] = {
    mapToSingleSuccessResponse(
      serviceCall[CheckpointV2DTO, CheckpointV2DTO](
        checkpointService.getCheckpoint(partitioningId, checkpointId)
      )
    )
  }

  override def getPartitioningCheckpoints(
    partitioningId: Long,
    limit: Option[Int],
    offset: Option[Long],
    checkpointName: Option[String] = None
  ): IO[ErrorResponse, PaginatedResponse[CheckpointV2DTO]] = {
    mapToPaginatedResponse(
      limit.get,
      offset.get,
      serviceCall[PaginatedResult[CheckpointV2DTO], PaginatedResult[CheckpointV2DTO]](
        checkpointService.getPartitioningCheckpoints(partitioningId, limit, offset, checkpointName)
      )
    )
  }
}

object CheckpointControllerImpl {
  val layer: URLayer[CheckpointService, CheckpointController] = ZLayer {
    for {
      checkpointService <- ZIO.service[CheckpointService]
    } yield new CheckpointControllerImpl(checkpointService)
  }
}

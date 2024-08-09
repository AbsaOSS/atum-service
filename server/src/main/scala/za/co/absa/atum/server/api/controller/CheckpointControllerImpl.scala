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

import za.co.absa.atum.model.dto.{CheckpointDTO, CheckpointV2DTO}
import za.co.absa.atum.server.api.http.ApiPaths.V2Paths
import za.co.absa.atum.server.api.service.CheckpointService
import za.co.absa.atum.server.model.ErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio._

import java.util.UUID

class CheckpointControllerImpl(checkpointService: CheckpointService) extends CheckpointController with BaseController {

  override def createCheckpointV1(
    checkpointDTO: CheckpointDTO
  ): IO[ErrorResponse, CheckpointDTO] = {
    serviceCall[Unit, CheckpointDTO](
      checkpointService.saveCheckpoint(checkpointDTO),
      _ => checkpointDTO
    )
  }

  override def postCheckpointV2(
    partitioningId: Long,
    checkpointV2DTO: CheckpointV2DTO
  ): IO[ErrorResponse, (SingleSuccessResponse[CheckpointV2DTO], String)] = {
    for {
      response <- mapToSingleSuccessResponse(
        serviceCall[Unit, CheckpointV2DTO](
          checkpointService.saveCheckpointV2(partitioningId, checkpointV2DTO),
          _ => checkpointV2DTO
        )
      )
      uri <- createResourceUri(
        Seq(V2Paths.Partitionings, partitioningId.toString, V2Paths.Checkpoints, checkpointV2DTO.id.toString)
      )
    } yield (response, uri)
  }

  override def getPartitioningCheckpointV2(
    partitioningId: Long,
    checkpointId: UUID
  ): IO[ErrorResponse, SingleSuccessResponse[CheckpointV2DTO]] = {
    mapToSingleSuccessResponse(
      serviceCall[CheckpointV2DTO, CheckpointV2DTO](
        checkpointService.getCheckpointV2(partitioningId, checkpointId),
        identity
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

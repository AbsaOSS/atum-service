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

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.service.PartitioningService
import za.co.absa.atum.server.model.{ErrorResponse, InternalServerErrorResponse}
import za.co.absa.atum.server.model.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import zio._

class PartitioningControllerImpl(partitioningService: PartitioningService)
    extends PartitioningController
    with BaseController {

  override def createPartitioningIfNotExistsV1(
    partitioningSubmitDTO: PartitioningSubmitDTO
  ): IO[ErrorResponse, AtumContextDTO] = {
    val atumContextDTOEffect = for {
      _ <- partitioningService
        .createPartitioningIfNotExists(partitioningSubmitDTO)
        .mapError(serviceError => InternalServerErrorResponse(serviceError.message))
      measures <- partitioningService
        .getPartitioningMeasures(partitioningSubmitDTO.partitioning)
        .mapError { serviceError: ServiceError =>
          InternalServerErrorResponse(serviceError.message)
        }
      additionalData <- partitioningService
        .getPartitioningAdditionalData(partitioningSubmitDTO.partitioning)
        .mapError { serviceError: ServiceError =>
          InternalServerErrorResponse(serviceError.message)
        }
    } yield AtumContextDTO(partitioningSubmitDTO.partitioning, measures.toSet, additionalData)

    atumContextDTOEffect
  }

  override def createPartitioningIfNotExistsV2(
    partitioningSubmitDTO: PartitioningSubmitDTO
  ): IO[ErrorResponse, SingleSuccessResponse[AtumContextDTO]] = {
    mapToSingleSuccessResponse(createPartitioningIfNotExistsV1(partitioningSubmitDTO))
  }

  override def createOrUpdateAdditionalDataV2(
    additionalData: AdditionalDataSubmitDTO
  ): IO[ErrorResponse, SingleSuccessResponse[AdditionalDataSubmitDTO]] = {
    mapToSingleSuccessResponse(
      serviceCall[Unit, AdditionalDataSubmitDTO](
        partitioningService.createOrUpdateAdditionalData(additionalData),
        _ => additionalData
      )
    )
  }

  override def getPartitioningCheckpointsV2(
    checkpointQueryDTO: CheckpointQueryDTO
  ): IO[ErrorResponse, MultiSuccessResponse[CheckpointDTO]] = {
    mapToMultiSuccessResponse(
      serviceCall[Seq[CheckpointDTO], Seq[CheckpointDTO]](
        partitioningService.getPartitioningCheckpoints(checkpointQueryDTO),
        identity
      )
    )
  }

}

object PartitioningControllerImpl {
  val layer: URLayer[PartitioningService, PartitioningController] = ZLayer {
    for {
      partitioningService <- ZIO.service[PartitioningService]
    } yield new PartitioningControllerImpl(partitioningService)
  }
}

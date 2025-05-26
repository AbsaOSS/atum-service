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

package za.co.absa.atum.server.api.v1.controller

import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.{ErrorResponse, InternalServerErrorResponse}
import za.co.absa.atum.server.api.common.controller.BaseController
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.v1.service.PartitioningService
import za.co.absa.atum.server.api.v2.service.{PartitioningService => PartitioningServiceV2}
import zio._

class PartitioningControllerImpl(
  partitioningService: PartitioningService,
  // unfortunately the controller needs to depend on the v2 service to get the partitioning measures and additional data
  // as the v1 service does not have this information (shouldn't have been introduced to v1 after its release)
  partitioningServiceV2: PartitioningServiceV2
) extends PartitioningController
    with BaseController {

  override def createPartitioningIfNotExists(
    partitioningSubmitDTO: PartitioningSubmitDTO
  ): IO[ErrorResponse, AtumContextDTO] = {
    for {
      _ <- partitioningService
        .createPartitioningIfNotExists(partitioningSubmitDTO)
        .mapError(serviceError => InternalServerErrorResponse(serviceError.message))

      partitioningWithId <- partitioningServiceV2
        .getPartitioning(partitioningSubmitDTO.partitioning)
        .mapError(serviceError => InternalServerErrorResponse(serviceError.message))

      measures <- partitioningServiceV2
        .getPartitioningMeasures(partitioningSubmitDTO.partitioning)
        .mapError { serviceError: ServiceError =>
          InternalServerErrorResponse(serviceError.message)
        }

      additionalData <- partitioningServiceV2
        .getPartitioningAdditionalData(partitioningWithId.id)
        .mapError { serviceError: ServiceError =>
          InternalServerErrorResponse(serviceError.message)
        }

      additionalDataForContext <- ZIO.succeed {
        additionalData.data.map { case (key, value) =>
          key -> value.map(_.value)
        }
      }

    } yield AtumContextDTO(partitioningSubmitDTO.partitioning, measures.toSet, additionalDataForContext)
  }

}

object PartitioningControllerImpl {
  val layer: URLayer[PartitioningService with PartitioningServiceV2, PartitioningController] = ZLayer {
    for {
      partitioningService <- ZIO.service[PartitioningService]
      partitioningServiceV2 <- ZIO.service[PartitioningServiceV2]
    } yield new PartitioningControllerImpl(partitioningService, partitioningServiceV2)
  }
}

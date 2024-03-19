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
import za.co.absa.atum.server.api.service.PartitioningService
import za.co.absa.atum.server.model.ErrorResponse
import zio._

class PartitioningControllerImpl(partitioningService: PartitioningService)
  extends PartitioningController with BaseController {

  override def createPartitioningIfNotExists(partitioning: PartitioningSubmitDTO): IO[ErrorResponse, AtumContextDTO] = {
    serviceCallWithStatus[Unit, AtumContextDTO](
      partitioningService.createPartitioningIfNotExists(partitioning),
      _ => {
        val measures: Set[MeasureDTO] = Set(MeasureDTO("count", Seq("*")))
        val additionalData: AdditionalDataDTO = Map.empty
        AtumContextDTO(partitioning.partitioning, measures, additionalData)
      }
    )
  }

  override def createOrUpdateAdditionalData(
    additionalData: AdditionalDataSubmitDTO
  ): IO[ErrorResponse, AdditionalDataSubmitDTO] = {
    serviceCallWithStatus[Unit, AdditionalDataSubmitDTO](
      partitioningService.createOrUpdateAdditionalData(additionalData),
      _ => additionalData
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

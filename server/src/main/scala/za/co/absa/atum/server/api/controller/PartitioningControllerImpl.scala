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
import za.co.absa.atum.server.api.service.PartitioningServiceImpl
import za.co.absa.atum.server.model.ErrorResponse
import zio._

class PartitioningControllerImpl(partitioningServiceImpl: PartitioningServiceImpl)
  extends PartitioningController with BaseController {

  override def createPartitioningIfNotExists(partitioning: PartitioningSubmitDTO): IO[ErrorResponse, AtumContextDTO] = {
    serviceCallWithStatus[Unit, AtumContextDTO](
      partitioningServiceImpl.returnAtumContext(partitioning),
      _ => AtumContextDTO(partitioning = partitioning.partitioning, measures = Set.empty, additionalData = Map.empty)
    )
  }

  override def createOrUpdateAdditionalData(
    additionalData: AdditionalDataSubmitDTO
  ): IO[ErrorResponse, AdditionalDataSubmitDTO] = {
    serviceCallWithStatus[Unit, AdditionalDataSubmitDTO](
      partitioningServiceImpl.createOrUpdateAdditionalData(additionalData),
      _ => additionalData
    )
  }

}

object PartitioningControllerImpl {
  val layer: URLayer[PartitioningServiceImpl, PartitioningController] = ZLayer {
    for {
      partitioningServiceImpl <- ZIO.service[PartitioningServiceImpl]
    } yield new PartitioningControllerImpl(partitioningServiceImpl)
  }
}

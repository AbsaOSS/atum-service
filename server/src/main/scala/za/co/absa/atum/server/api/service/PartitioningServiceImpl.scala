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
import za.co.absa.atum.server.api.repository.PartitioningRepository
import za.co.absa.atum.server.model.CheckpointFromDB
import zio._

class PartitioningServiceImpl(partitioningRepository: PartitioningRepository)
    extends PartitioningService
    with BaseService {

  override def createPartitioningIfNotExists(partitioningSubmitDTO: PartitioningSubmitDTO): IO[ServiceError, Unit] = {
    repositoryCall(
      partitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO),
      "createPartitioningIfNotExists"
    )
  }

  override def createOrUpdateAdditionalData(additionalData: AdditionalDataSubmitDTO): IO[ServiceError, Unit] = {
    repositoryCall(
      partitioningRepository.createOrUpdateAdditionalData(additionalData),
      "createOrUpdateAdditionalData"
    )
  }

  override def getPartitioningMeasures(partitioning: PartitioningDTO): IO[ServiceError, Seq[MeasureDTO]] = {
    repositoryCall(
      partitioningRepository.getPartitioningMeasures(partitioning),
      "getPartitioningMeasures"
    )
  }

  override def getPartitioningAdditionalData(partitioning: PartitioningDTO): IO[ServiceError, InitialAdditionalDataDTO] = {
    repositoryCall(
      partitioningRepository.getPartitioningAdditionalData(partitioning),
      "getPartitioningAdditionalData"
    )
  }

  override def getPartitioningCheckpoints(
    checkpointQueryDTO: CheckpointQueryDTO
  ): IO[ServiceError, Seq[CheckpointDTO]] = {
    for {
      checkpointsFromDB <- repositoryCall(
        partitioningRepository.getPartitioningCheckpoints(checkpointQueryDTO),
        "getPartitioningCheckpoints"
      )
      checkpointDTOs <- ZIO.foreach(checkpointsFromDB) { checkpointFromDB =>
        ZIO
          .fromEither(CheckpointFromDB.toCheckpointDTO(checkpointQueryDTO.partitioning, checkpointFromDB))
          .mapError(error => ServiceError(error.getMessage))
      }
    } yield checkpointDTOs

  }
}

object PartitioningServiceImpl {
  val layer: URLayer[PartitioningRepository, PartitioningService] = ZLayer {
    for {
      partitioningRepository <- ZIO.service[PartitioningRepository]
    } yield new PartitioningServiceImpl(partitioningRepository)
  }
}

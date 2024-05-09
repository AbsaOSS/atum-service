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

import za.co.absa.atum.model.dto.{
  AdditionalDataDTO, AdditionalDataSubmitDTO, CheckpointQueryDTO,
  CheckpointQueryResultDTO, MeasureDTO, PartitioningDTO, PartitioningSubmitDTO
}
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.repository.PartitioningRepository
import za.co.absa.fadb.exceptions.StatusException
import za.co.absa.atum.server.api.exception.DatabaseError
import zio._

class PartitioningServiceImpl(partitioningRepository: PartitioningRepository)
  extends PartitioningService with BaseService {

  override def createPartitioningIfNotExists(partitioningSubmitDTO: PartitioningSubmitDTO):
  IO[ServiceError, Either[StatusException, Unit]] = {
    repositoryCallWithStatus(
      partitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO), "createPartitioningIfNotExists"
    ).mapError(error => ServiceError(error.message))
  }

  override def createOrUpdateAdditionalData(
    additionalData: AdditionalDataSubmitDTO
  ): IO[ServiceError, Either[StatusException, Unit]] = {
    repositoryCallWithStatus(
      partitioningRepository.createOrUpdateAdditionalData(additionalData), "createOrUpdateAdditionalData"
    ).mapError(error => ServiceError(error.message))
  }

  override def getPartitioningMeasures(partitioning: PartitioningDTO): IO[ServiceError, Seq[MeasureDTO]] = {
    partitioningRepository.getPartitioningMeasures(partitioning)
      .mapError { case DatabaseError(message) =>
        ServiceError(s"Failed to retrieve partitioning measures': $message")
      }
  }

  override def getPartitioningAdditionalData(partitioning: PartitioningDTO): IO[ServiceError, AdditionalDataDTO] = {
    partitioningRepository.getPartitioningAdditionalData(partitioning)
      .mapError { case DatabaseError(message) =>
        ServiceError(s"Failed to retrieve partitioning additional data': $message")
      }
  }

  override def getPartitioningCheckpoints(checkpointQueryDTO: CheckpointQueryDTO):
  IO[ServiceError, Seq[CheckpointQueryResultDTO]] = {
    repositoryCall(
      partitioningRepository.getPartitioningCheckpoints(checkpointQueryDTO), "getPartitioningCheckpoint"
    )
  }

}

object PartitioningServiceImpl {
  val layer: URLayer[PartitioningRepository, PartitioningService] = ZLayer {
    for {
      partitioningRepository <- ZIO.service[PartitioningRepository]
    } yield new PartitioningServiceImpl(partitioningRepository)
  }
}

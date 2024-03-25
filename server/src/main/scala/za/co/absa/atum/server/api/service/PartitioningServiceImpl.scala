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

import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataSubmitDTO, AtumContextDTO, MeasureDTO, PartitioningSubmitDTO}
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.repository.PartitioningRepository
import za.co.absa.fadb.exceptions.StatusException
import zio._

class PartitioningServiceImpl(partitioningRepository: PartitioningRepository)
  extends PartitioningService with BaseService {

  override def createPartitioningIfNotExists(
    partitioning: PartitioningSubmitDTO
  ): IO[ServiceError, Either[StatusException, Unit]] = {
    repositoryCallWithStatus(
      partitioningRepository.createPartitioningIfNotExists(partitioning), "createPartitioningIfNotExists"
    )
  }

  override def createOrUpdateAdditionalData(
    additionalData: AdditionalDataSubmitDTO
  ): IO[ServiceError, Either[StatusException, Unit]] = {
    repositoryCallWithStatus(
      partitioningRepository.createOrUpdateAdditionalData(additionalData), "createOrUpdateAdditionalData"
    )
  }

  override def getPartitioningMeasures(partitioning: PartitioningSubmitDTO): IO[ServiceError, Either[StatusException, Seq[MeasureDTO]]] = {
    repositoryCallWithStatus(
      partitioningRepository.getPartitioningMeasures(partitioning), "getPartitioningMeasures"
    )
  }

  override def getPartitioningAdditionalData(partitioning: PartitioningSubmitDTO): IO[ServiceError, Either[StatusException, AdditionalDataDTO]] = {
    repositoryCallWithStatus(
      partitioningRepository.getPartitioningAdditionalData(partitioning), "getPartitioningAdditionalData"
    )
  }

  def returnAtumContext(partitioning: PartitioningSubmitDTO): IO[ServiceError, AtumContextDTO] = {
    for {
      partitioning <- createPartitioningIfNotExists(partitioning)
        .flatMap {
          case Left(_) => ZIO.fail(ServiceError("Failed to create or retrieve partitioning"))
          case Right(_) => ZIO.succeed(partitioning)
        }
        .mapError(error => ServiceError(error.message))

      additionalData <- getPartitioningAdditionalData(partitioning)
        .flatMap {
          case Left(_) => ZIO.succeed(Map[String, Option[String]])
          case Right(value) => ZIO.succeed(value)
        }
        .mapError(error => ServiceError(error.message))

      measures <- getPartitioningMeasures(partitioning)
        .flatMap {
          case Left(_) => ZIO.succeed(Set.empty[MeasureDTO])
          case Right(value) => ZIO.succeed(value.toSet)
        }
        .mapError(error => ServiceError(error.message))
    } yield AtumContextDTO(partitioning.partitioning, measures, additionalData)
  }
}

object PartitioningServiceImpl {
  val layer: URLayer[PartitioningRepository, PartitioningService] = ZLayer {
    for {
      partitioningRepository <- ZIO.service[PartitioningRepository]
    } yield new PartitioningServiceImpl(partitioningRepository)
  }
}

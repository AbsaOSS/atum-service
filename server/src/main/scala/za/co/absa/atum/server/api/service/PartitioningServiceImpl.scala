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
import za.co.absa.atum.server.api.exception.DatabaseError
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
    ).mapError(error => ServiceError(error.message))
  }

  override def getPartitioningMeasures(partitioning: PartitioningSubmitDTO): IO[ServiceError, Seq[MeasureDTO]] = {
    partitioningRepository.getPartitioningMeasures(partitioning)
      .mapError { case DatabaseError(message) =>
        ServiceError(s"Failed to retrieve partitioning measures': $message")
      }
  }

  override def getPartitioningAdditionalData(partitioning: PartitioningSubmitDTO): IO[ServiceError, Seq[AdditionalDataDTO]] = {
    partitioningRepository.getPartitioningAdditionalData(partitioning)
      .mapError { case DatabaseError(message) =>
        ServiceError(s"Failed to retrieve partitioning additional data': $message")
      }
  }

  override def returnAtumContext(partitioning: PartitioningSubmitDTO): IO[ServiceError, AtumContextDTO] = {
    for {
      partitioning <- createPartitioningIfNotExists(partitioning)
        .flatMap {
          case Left(_) => ZIO.fail(ServiceError("Failed to create or retrieve partitioning"))
          case Right(_) => ZIO.succeed(partitioning)
        }
        .mapError(error => ServiceError(error.message))

//      additionalData <- getPartitioningAdditionalData(partitioning)
//        .fold(_ => Map[String, Option[String]], value => value)
      additionalData <- getPartitioningAdditionalData(partitioning)
        .fold(_ => Seq.empty[AdditionalDataDTO], value => value)
        .map(_.headOption)
        .flatMap {
          case Some(data) => ZIO.succeed(data)
          case None => ZIO.fail(ServiceError("No additional data found"))
        }
//      additionalData <- getPartitioningAdditionalData(partitioning)
//        .flatMap(data => ZIO.fromOption(data.headOption).orElseFail(ServiceError("No additional data found")))

//      measures <- getPartitioningMeasures(partitioning)
//        .fold(_ => Seq.empty[MeasureDTO], value => value)
      measures <- getPartitioningMeasures(partitioning)
        .fold(_ => Seq.empty[MeasureDTO], value => value)
        .map(_.headOption)
        .flatMap{
          case Some(measure) => ZIO.succeed(measure)
          case None => ZIO.fail(ServiceError("No measures found"))
        }
//      measures <- getPartitioningMeasures(partitioning)
//        .flatMap(measures => ZIO.fromOption(measures.headOption).orElseFail(ServiceError("No measures found")))

    } yield AtumContextDTO(partitioning.partitioning, Set(measures), additionalData)
  }

}

object PartitioningServiceImpl {
  val layer: URLayer[PartitioningRepository, PartitioningService] = ZLayer {
    for {
      partitioningRepository <- ZIO.service[PartitioningRepository]
    } yield new PartitioningServiceImpl(partitioningRepository)
  }
}

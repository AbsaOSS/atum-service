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
  AdditionalDataDTO, AdditionalDataSubmitDTO, CheckpointDTO, CheckpointQueryDTO,
  MeasureDTO, MeasureResultDTO, MeasurementDTO, PartitioningDTO, PartitioningSubmitDTO
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

  override def getPartitioningCheckpoints(checkpointQueryDTO: CheckpointQueryDTO): IO[ServiceError, Seq[CheckpointDTO]] = {
    //    repositoryCall(
    //      partitioningRepository.getPartitioningCheckpoints(checkpointQueryDTO), "getPartitioningCheckpoint"
    //    ).map {
    //      checkpointMeasurementsSeq =>
    //        checkpointMeasurementsSeq.map { cm =>
    //          CheckpointDTO(
    //            id = cm.idCheckpoint,
    //            name = cm.checkpointName,
    //            author = cm.author,
    //            measuredByAtumAgent = cm.measuredByAtumAgent,
    //            partitioning = checkpointQueryDTO.partitioning,
    //            processStartTime = cm.checkpointStartTime,
    //            processEndTime = cm.checkpointEndTime,
    //            measurements = Set(
    //              MeasurementDTO(
    //                measure = MeasureDTO(
    //                  measureName = cm.measureName,
    //                  measuredColumns = cm.measureColumns
    //                ),
    //                result = MeasureResultDTO(
    //                  mainValue = MeasureResultDTO.TypedValue(
    //                    value = cm.measurementValue.hcursor.downField("value").as[String].getOrElse(""),
    //                    valueType = cm.measurementValue.hcursor.downField("valueType").as[String].getOrElse("") match {
    //                      case "String" => MeasureResultDTO.ResultValueType.String
    //                      case "Long" => MeasureResultDTO.ResultValueType.Long
    //                      case "BigDecimal" => MeasureResultDTO.ResultValueType.BigDecimal
    //                      case "Double" => MeasureResultDTO.ResultValueType.Double
    //                      case _ => MeasureResultDTO.ResultValueType.String
    //                    }
    //                  ),
    //                  supportValues = Map.empty
    //                )
    //              )
    //            )
    //          )
    //        }
    //    }
    for {
      checkpointsFromDB <- repositoryCall(partitioningRepository.getPartitioningCheckpoints(checkpointQueryDTO), "getPartitioningCheckpoints")
      checkpointDTOs <- ZIO.foreach(checkpointsFromDB) {checkpointsFromDB =>
        ZIO.fromEither(CheckpointsFromDB.toCheckpointDTO(checkpointQueryDTO.partitioning, checkpointsFromDB)
          .mapError{ case DatabaseError(message) =>
            ServiceError(s"Failed to convert checkpoint to checkpointDTO: $message")
          }
        )
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

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

package za.co.absa.atum.server.model

import za.co.absa.atum.model.dto.{
  CheckpointDTO,
  CheckpointV2DTO,
  MeasureDTO,
  MeasureResultDTO,
  MeasurementDTO,
  PartitioningDTO
}
import za.co.absa.atum.model.dto.{
  CheckpointDTO,
  CheckpointV2DTO,
  MeasureDTO,
  MeasureResultDTO,
  MeasurementDTO,
  PartitioningDTO
}
import io.circe.{DecodingFailure, Json}

import java.time.ZonedDateTime
import java.util.UUID

case class CheckpointFromDB(
  idCheckpoint: Option[UUID],
  checkpointName: Option[String],
  author: Option[String],
  measuredByAtumAgent: Option[Boolean],
  measureName: Option[String],
  measuredColumns: Option[Seq[String]],
  measurementValue: Option[
    Json
  ], // it's easier to convert this attribute to our `MeasurementDTO` after we received this as JSON from DB
  checkpointStartTime: Option[ZonedDateTime],
  checkpointEndTime: Option[ZonedDateTime]
)

object CheckpointFromDB {

  def toCheckpointDTO(
    partitioning: PartitioningDTO,
    checkpointQueryResult: CheckpointFromDB
  ): Either[DecodingFailure, CheckpointDTO] = {
    val measureResultOrErr = checkpointQueryResult.measurementValue.get.as[MeasureResultDTO]

    measureResultOrErr match {
      case Left(err) => Left(err)
      case Right(measureResult) =>
        Right(
          CheckpointDTO(
            id = checkpointQueryResult.idCheckpoint.get,
            name = checkpointQueryResult.checkpointName.get,
            author = checkpointQueryResult.author.get,
            measuredByAtumAgent = checkpointQueryResult.measuredByAtumAgent.get,
            partitioning = partitioning,
            processStartTime = checkpointQueryResult.checkpointStartTime.get,
            processEndTime = checkpointQueryResult.checkpointEndTime,
            measurements = Set(
              MeasurementDTO(
                measure = MeasureDTO(
                  measureName = checkpointQueryResult.measureName.get,
                  measuredColumns = checkpointQueryResult.measuredColumns.get
                ),
                result = measureResult
              )
            )
          )
        )
    }
  }

  def toCheckpointV2DTO(
//                         partitioningId: Long,
    checkpointQueryResult: CheckpointFromDB
  ): Either[DecodingFailure, CheckpointV2DTO] = {
    val measureResultOrErr = checkpointQueryResult.measurementValue.get.as[MeasureResultDTO]

    measureResultOrErr match {
      case Left(err) => Left(err)
      case Right(measureResult) =>
        Right(
          CheckpointV2DTO(
            id = checkpointQueryResult.idCheckpoint.get,
            name = checkpointQueryResult.checkpointName.get,
            author = checkpointQueryResult.author.get,
            measuredByAtumAgent = checkpointQueryResult.measuredByAtumAgent.get,
//            partitioningId = partitioningId,
            processStartTime = checkpointQueryResult.checkpointStartTime.get,
            processEndTime = checkpointQueryResult.checkpointEndTime,
            measurements = Set(
              MeasurementDTO(
                measure = MeasureDTO(
                  measureName = checkpointQueryResult.measureName.get,
                  measuredColumns = checkpointQueryResult.measuredColumns.get
                ),
                result = measureResult
              )
            )
          )
        )
    }
  }

}

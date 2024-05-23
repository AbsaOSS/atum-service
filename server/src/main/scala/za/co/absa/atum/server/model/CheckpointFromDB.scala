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

import za.co.absa.atum.model.dto.{CheckpointDTO, MeasureDTO, MeasureResultDTO, MeasurementDTO, PartitioningDTO}
import io.circe.{Error, Json}
import io.circe.generic.auto._

import java.time.ZonedDateTime
import java.util.UUID

import za.co.absa.atum.server.api.database.DoobieImplicits.decodeResultValueType

case class CheckpointFromDB(
   idCheckpoint: UUID,
   checkpointName: String,
   author: String,
   measuredByAtumAgent: Boolean = false,
   measureName: String,
   measuredColumns: Seq[String],
   measurementValue: Json,  // it's easier to convert this attribute to our `MeasurementDTO` after we received this as JSON from DB
   checkpointStartTime: ZonedDateTime,
   checkpointEndTime:  Option[ZonedDateTime]
 )

object CheckpointFromDB {

  private def extractMainValue(json: Json): Either[Error, MeasureResultDTO.TypedValue] =
    json.as[MeasureResultDTO].map(_.mainValue)

  private def extractSupportValues(json: Json): Either[Error, Map[String, MeasureResultDTO.TypedValue]] =
    json.as[MeasureResultDTO].map(_.supportValues)

  def toCheckpointDTO(partitioning: PartitioningDTO, checkpointQueryResult: CheckpointFromDB): CheckpointDTO = {

    val measureResultOrErr = for {
      mainValue <- extractMainValue(checkpointQueryResult.measurementValue)
      supportValues <- extractSupportValues(checkpointQueryResult.measurementValue)
    } yield MeasureResultDTO(mainValue, supportValues)

    measureResultOrErr match {
      case Left(err) => throw err
      case Right(measureResult) =>
        CheckpointDTO(
          id = checkpointQueryResult.idCheckpoint,
          name = checkpointQueryResult.checkpointName,
          author = checkpointQueryResult.author,
          measuredByAtumAgent = checkpointQueryResult.measuredByAtumAgent,
          partitioning = partitioning,
          processStartTime = checkpointQueryResult.checkpointStartTime,
          processEndTime = checkpointQueryResult.checkpointEndTime,
          measurements = Set(
            MeasurementDTO(
              measure = MeasureDTO(
                measureName = checkpointQueryResult.measureName,
                measuredColumns = checkpointQueryResult.measuredColumns
              ),
              result = measureResult
            )
          )
        )
    }
  }

}

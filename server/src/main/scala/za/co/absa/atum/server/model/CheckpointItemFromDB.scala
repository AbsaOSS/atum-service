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


import io.circe.{DecodingFailure, Json}
import za.co.absa.atum.model.dto.{CheckpointV2DTO, MeasureDTO, MeasureResultDTO, MeasurementDTO}

import java.time.ZonedDateTime
import java.util.UUID

case class CheckpointItemFromDB(
  idCheckpoint: UUID,
  checkpointName: String,
  author: String,
  measuredByAtumAgent: Boolean,
  measureName: String,
  measuredColumns: Seq[String],
  measurementValue: Json, // JSON representation of `MeasurementDTO`
  checkpointStartTime: ZonedDateTime,
  checkpointEndTime: Option[ZonedDateTime],
  hasMore: Boolean
)

object CheckpointItemFromDB {

  def fromItemsToCheckpointV2DTO(
    checkpointItems: Seq[CheckpointItemFromDB]
  ): Either[DecodingFailure, CheckpointV2DTO] = {
    val measurementsOrErr = checkpointItems.map { checkpointItem =>
      checkpointItem.measurementValue.as[MeasureResultDTO].map { measureResult =>
        MeasurementDTO(
          measure = MeasureDTO(
            measureName = checkpointItem.measureName,
            measuredColumns = checkpointItem.measuredColumns
          ),
          result = measureResult
        )
      }
    }

    val errors = measurementsOrErr.collect { case Left(err) => err }

    if (errors.nonEmpty) {
      Left(errors.head)
    } else {
      val measurements = measurementsOrErr.collect { case Right(measurement) => measurement }.toSet
      Right(
        CheckpointV2DTO(
          id = checkpointItems.head.idCheckpoint,
          name = checkpointItems.head.checkpointName,
          author = checkpointItems.head.author,
          measuredByAtumAgent = checkpointItems.head.measuredByAtumAgent,
          processStartTime = checkpointItems.head.checkpointStartTime,
          processEndTime = checkpointItems.head.checkpointEndTime,
          measurements = measurements
        )
      )
    }
  }

  def groupAndConvertItemsToCheckpointV2DTOs(
    checkpointItems: Seq[CheckpointItemFromDB]
  ): Either[DecodingFailure, Seq[CheckpointV2DTO]] = {
    val groupedItems = checkpointItems.groupBy(_.idCheckpoint)
    val orderedIds = checkpointItems.map(_.idCheckpoint).distinct

    val result = orderedIds.map { id =>
      CheckpointItemFromDB.fromItemsToCheckpointV2DTO(groupedItems(id))
    }

    val errors = result.collect { case Left(err) => err }
    if (errors.nonEmpty) {
      Left(errors.head)
    } else {
      Right(result.collect { case Right(dto) => dto })
    }
  }

}

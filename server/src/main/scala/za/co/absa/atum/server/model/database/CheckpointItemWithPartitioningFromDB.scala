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

package za.co.absa.atum.server.model.database

import io.circe.Json
import za.co.absa.atum.model.dto._

import java.time.ZonedDateTime
import java.util.UUID

case class CheckpointItemWithPartitioningFromDB(
  idCheckpoint: UUID,
  checkpointName: String,
  author: String,
  measuredByAtumAgent: Boolean,
  measureName: String,
  measuredColumns: Seq[String],
  measurementValue: Json, // JSON representation of `MeasurementDTO`
  checkpointStartTime: ZonedDateTime,
  checkpointEndTime: Option[ZonedDateTime],
  idPartitioning: Long,
  partitioning: Json, // JSON representation of `PartitioningForDB`
  partitioningAuthor: String,
  hasMore: Boolean
)

object CheckpointItemWithPartitioningFromDB {

  private def fromItemsToCheckpointWithPartitioningDTO(
    checkpointItems: Seq[CheckpointItemWithPartitioningFromDB]
  ): Either[Throwable, CheckpointWithPartitioningDTO] = {
    for {
      measurements <- extractMeasurements(checkpointItems)
      partitioning <- extractPartitioning(checkpointItems)
    } yield {
      CheckpointWithPartitioningDTO(
        id = checkpointItems.head.idCheckpoint,
        name = checkpointItems.head.checkpointName,
        author = checkpointItems.head.author,
        measuredByAtumAgent = checkpointItems.head.measuredByAtumAgent,
        processStartTime = checkpointItems.head.checkpointStartTime,
        processEndTime = checkpointItems.head.checkpointEndTime,
        measurements = measurements.toSet,
        partitioning
      )
    }
  }

  private def extractMeasurements(
    checkpointItems: Seq[CheckpointItemWithPartitioningFromDB]
  ): Either[Throwable, Seq[MeasurementDTO]] = {
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
    measurementsOrErr
      .collectFirst { case Left(err) => Left(err) }
      .getOrElse(Right(measurementsOrErr.collect { case Right(measurement) => measurement }))
  }

  private def extractPartitioning(
    checkpointItems: Seq[CheckpointItemWithPartitioningFromDB]
  ): Either[Throwable, PartitioningWithIdDTO] = {
    checkpointItems.head.partitioning.as[PartitioningForDB].map { partitioningForDB =>
      val partitioningDTO = partitioningForDB.keys.map { key =>
        PartitionDTO(key, partitioningForDB.keysToValuesMap(key))
      }
      PartitioningWithIdDTO(
        id = checkpointItems.head.idPartitioning,
        partitioning = partitioningDTO,
        author = checkpointItems.head.partitioningAuthor
      )
    }
  }

  def groupAndConvertItemsToCheckpointWithPartitioningDTOs(
    checkpointItems: Seq[CheckpointItemWithPartitioningFromDB]
  ): Either[Throwable, Seq[CheckpointWithPartitioningDTO]] = {
    val groupedItems = checkpointItems.groupBy(_.idCheckpoint)
    val orderedCheckpointIds = checkpointItems
      .sortBy(_.checkpointStartTime)(Ordering[ZonedDateTime].reverse)
      .map(_.idCheckpoint)
      .distinct

    val result = orderedCheckpointIds.map { id => fromItemsToCheckpointWithPartitioningDTO(groupedItems(id)) }

    val error = result.collectFirst { case Left(err) => Left(err) }
    error.getOrElse(Right(result.collect { case Right(dto) => dto }))
  }

}

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

package za.co.absa.atum.server.api.repository

import io.circe.DecodingFailure
import za.co.absa.atum.server.api.database.runs.functions.{GetCheckpointV2, WriteCheckpoint, WriteCheckpointV2}
import za.co.absa.atum.model.dto.{CheckpointDTO, CheckpointV2DTO, MeasureDTO, MeasureResultDTO, MeasurementDTO}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import za.co.absa.atum.server.model.{CheckpointItemFromDB, GetCheckpointV2Args, WriteCheckpointV2Args}
import zio._
import zio.interop.catz.asyncInstance

import java.util.UUID

class CheckpointRepositoryImpl(
  writeCheckpointFn: WriteCheckpoint,
  writeCheckpointV2Fn: WriteCheckpointV2,
  getCheckpointV2Fn: GetCheckpointV2
) extends CheckpointRepository
    with BaseRepository {

  override def writeCheckpoint(checkpointDTO: CheckpointDTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(writeCheckpointFn(checkpointDTO), "writeCheckpoint")
  }

  override def writeCheckpointV2(partitioningId: Long, checkpointV2DTO: CheckpointV2DTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(
      writeCheckpointV2Fn(WriteCheckpointV2Args(partitioningId, checkpointV2DTO)),
      "writeCheckpoint"
    )
  }

  override def getCheckpointV2(partitioningId: Long, checkpointId: UUID): IO[DatabaseError, CheckpointV2DTO] = {
    dbMultipleResultCallWithAggregatedStatus(
      getCheckpointV2Fn(GetCheckpointV2Args(partitioningId, checkpointId)),
      "getCheckpoint"
    )
      .map(_.flatten)
      .flatMap { checkpointItems =>
        ZIO
          .fromEither(checkpointItemsToCheckpointV2DTO(checkpointItems))
          .mapError(error => GeneralDatabaseError(error.getMessage))
      }
  }

  private def checkpointItemsToCheckpointV2DTO(
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

}

object CheckpointRepositoryImpl {
  val layer: URLayer[WriteCheckpoint with WriteCheckpointV2 with GetCheckpointV2, CheckpointRepository] = ZLayer {
    for {
      writeCheckpoint <- ZIO.service[WriteCheckpoint]
      writeCheckpointV2 <- ZIO.service[WriteCheckpointV2]
      getCheckpointV2 <- ZIO.service[GetCheckpointV2]
    } yield new CheckpointRepositoryImpl(writeCheckpoint, writeCheckpointV2, getCheckpointV2)
  }
}

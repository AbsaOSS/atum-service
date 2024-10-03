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

import za.co.absa.atum.model.dto.{CheckpointDTO, CheckpointV2DTO}
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningCheckpointV2.GetPartitioningCheckpointV2Args
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningCheckpoints.GetPartitioningCheckpointsArgs
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpointV2.WriteCheckpointArgs
import za.co.absa.atum.server.api.database.runs.functions.{WriteCheckpoint, WriteCheckpointV2}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.atum.server.model.{CheckpointItemFromDB, PaginatedResult}
import zio._
import zio.interop.catz.asyncInstance

import java.util.UUID

class CheckpointRepositoryImpl(
  writeCheckpointFn: WriteCheckpoint,
  writeCheckpointV2Fn: WriteCheckpointV2,
  getCheckpointV2Fn: GetPartitioningCheckpointV2,
  getPartitioningCheckpoints: GetPartitioningCheckpoints
) extends CheckpointRepository
    with BaseRepository {

  override def writeCheckpoint(checkpointDTO: CheckpointDTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(writeCheckpointFn(checkpointDTO), "writeCheckpoint")
  }

  override def writeCheckpointV2(partitioningId: Long, checkpointV2DTO: CheckpointV2DTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(
      writeCheckpointV2Fn(WriteCheckpointArgs(partitioningId, checkpointV2DTO)),
      "writeCheckpoint"
    )
  }

  override def getCheckpointV2(partitioningId: Long, checkpointId: UUID): IO[DatabaseError, CheckpointV2DTO] = {
    dbMultipleResultCallWithAggregatedStatus(
      getCheckpointV2Fn(GetPartitioningCheckpointV2Args(partitioningId, checkpointId)),
      "getCheckpoint"
    )
      .map(_.flatten)
      .flatMap { checkpointItems =>
        ZIO
          .fromEither(CheckpointItemFromDB.fromItemsToCheckpointV2DTO(checkpointItems))
          .mapError(error => GeneralDatabaseError(error.getMessage))
      }
  }

  override def getPartitioningCheckpoints(
    partitioningId: Long,
    limit: Option[Int],
    offset: Option[Long],
    checkpointName: Option[String]
  ): IO[DatabaseError, PaginatedResult[CheckpointV2DTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getPartitioningCheckpoints(GetPartitioningCheckpointsArgs(partitioningId, limit, offset, checkpointName)),
      "getPartitioningCheckpoints"
    )
      .map(_.flatten)
      .flatMap { checkpointItems =>
        ZIO
          .fromEither(CheckpointItemFromDB.groupAndConvertItemsToCheckpointV2DTOs(checkpointItems))
          .mapBoth(
            error => GeneralDatabaseError(error.getMessage),
            checkpoints =>
              if (checkpointItems.nonEmpty && checkpointItems.head.hasMore) ResultHasMore(checkpoints)
              else ResultNoMore(checkpoints)
          )
      }
  }

}

object CheckpointRepositoryImpl {
  val layer: URLayer[
    WriteCheckpoint with WriteCheckpointV2 with GetPartitioningCheckpointV2 with GetPartitioningCheckpoints,
    CheckpointRepository
  ] =
    ZLayer {
      for {
        writeCheckpoint <- ZIO.service[WriteCheckpoint]
        writeCheckpointV2 <- ZIO.service[WriteCheckpointV2]
        getCheckpointV2 <- ZIO.service[GetPartitioningCheckpointV2]
        getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
      } yield new CheckpointRepositoryImpl(
        writeCheckpoint,
        writeCheckpointV2,
        getCheckpointV2,
        getPartitioningCheckpoints
      )
    }
}

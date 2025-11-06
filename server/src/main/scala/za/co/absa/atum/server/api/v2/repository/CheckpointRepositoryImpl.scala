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

package za.co.absa.atum.server.api.v2.repository

import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.server.api.common.repository.{BaseRepository, CheckpointPropertiesEnricher}
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningCheckpointV2.GetPartitioningCheckpointV2Args
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningCheckpoints.GetPartitioningCheckpointsArgs
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpointV2.WriteCheckpointArgs
import za.co.absa.atum.server.api.database.runs.functions.GetCheckpointProperties
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import za.co.absa.atum.server.model.PaginatedResult
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.atum.server.model.database.CheckpointItemFromDB
import zio._
import zio.interop.catz.asyncInstance

import java.util.UUID

class CheckpointRepositoryImpl(
  writeCheckpointV2Fn: WriteCheckpointV2,
  getCheckpointV2Fn: GetPartitioningCheckpointV2,
  getPartitioningCheckpointsFn: GetPartitioningCheckpoints,
  override val getCheckpointPropertiesFn: GetCheckpointProperties
) extends CheckpointRepository with BaseRepository with CheckpointPropertiesEnricher {

  override def writeCheckpoint(partitioningId: Long, checkpointV2DTO: CheckpointV2DTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(
      writeCheckpointV2Fn(WriteCheckpointArgs(partitioningId, checkpointV2DTO)),
      "writeCheckpoint"
    )
  }

  override def getCheckpoint(
    partitioningId: Long,
    checkpointId: UUID,
    includeProperties: Boolean
  ): IO[DatabaseError, CheckpointV2DTO] = {
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
      .flatMap { checkpoint =>
        if (includeProperties) enrichWithProperties(checkpoint)
        else ZIO.succeed(checkpoint)
      }
  }

  override def getPartitioningCheckpoints(
    partitioningId: Long,
    limit: Int,
    offset: Long,
    checkpointName: Option[String],
    includeProperties: Boolean
  ): IO[DatabaseError, PaginatedResult[CheckpointV2DTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getPartitioningCheckpointsFn(GetPartitioningCheckpointsArgs(partitioningId, limit, offset, checkpointName)),
      "getPartitioningCheckpoints"
    )
      .map(_.flatten)
      .flatMap { checkpointItems =>
        ZIO
          .fromEither(CheckpointItemFromDB.groupAndConvertItemsToCheckpointV2DTOs(checkpointItems))
          .mapError(error => GeneralDatabaseError(error.getMessage))
          .flatMap { checkpoints =>
            val checkpointsF =
              if (includeProperties) ZIO.foreachPar(checkpoints)(enrichWithProperties)
              else ZIO.succeed(checkpoints)
            val resultCtor: Seq[CheckpointV2DTO] => PaginatedResult[CheckpointV2DTO] =
              if (checkpointItems.nonEmpty && checkpointItems.head.hasMore)
                ResultHasMore.apply[CheckpointV2DTO]
              else ResultNoMore.apply[CheckpointV2DTO]
            checkpointsF.map(resultCtor)
          }
      }
  }

}

object CheckpointRepositoryImpl {
  val layer: URLayer[
    WriteCheckpointV2 with GetPartitioningCheckpointV2 with GetPartitioningCheckpoints with GetCheckpointProperties,
    CheckpointRepository
  ] =
    ZLayer {
      for {
        writeCheckpointV2 <- ZIO.service[WriteCheckpointV2]
        getCheckpointV2 <- ZIO.service[GetPartitioningCheckpointV2]
        getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
        getCheckpointProperties <- ZIO.service[GetCheckpointProperties]
      } yield new CheckpointRepositoryImpl(
        writeCheckpointV2,
        getCheckpointV2,
        getPartitioningCheckpoints,
        getCheckpointProperties
      )
    }
}

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

import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.api.database.runs.functions.{GetCheckpointV2, WriteCheckpoint, WriteCheckpointV2}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.model.WriteCheckpointV2Args
import zio._
import zio.interop.catz.asyncInstance

class CheckpointRepositoryImpl(
  writeCheckpointFn: WriteCheckpoint,
  writeCheckpointV2Fn: WriteCheckpointV2,
  getCheckpointV2Fn: GetCheckpointV2
) extends CheckpointRepository
    with BaseRepository {

  override def writeCheckpoint(checkpointDTO: CheckpointDTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(writeCheckpointFn(checkpointDTO), "writeCheckpoint")
  }

  override def writeCheckpointV2(partitioningId: Long, checkpointDTO: CheckpointDTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(
      writeCheckpointV2Fn(WriteCheckpointV2Args(partitioningId, checkpointDTO)),
      "writeCheckpoint"
    )
  }

  override def getCheckpointV2(partitioningId: Long, checkpointId: String): IO[DatabaseError, CheckpointDTO] = {
    dbSingleResultCallWithStatus(getCheckpointV2Fn(partitioningId, checkpointId), "getCheckpoint")
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

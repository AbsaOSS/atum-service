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
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpoint.WriteCheckpointArgs
import za.co.absa.atum.server.api.database.runs.functions.{WriteCheckpoint, WriteCheckpointV1}
import za.co.absa.atum.server.api.exception.DatabaseError
import zio._
import zio.interop.catz.asyncInstance

class CheckpointRepositoryImpl(writeCheckpointV1Fn: WriteCheckpointV1, writeCheckpointFn: WriteCheckpoint)
    extends CheckpointRepository
    with BaseRepository {

  override def writeCheckpoint(checkpointDTO: CheckpointDTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(writeCheckpointV1Fn(checkpointDTO), "writeCheckpoint")
  }

  override def writeCheckpointV2(partitioningId: Long, checkpointV2DTO: CheckpointV2DTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(
      writeCheckpointFn(WriteCheckpointArgs(partitioningId, checkpointV2DTO)),
      "writeCheckpoint"
    )
  }
}

object CheckpointRepositoryImpl {
  val layer: URLayer[WriteCheckpointV1 with WriteCheckpoint, CheckpointRepository] = ZLayer {
    for {
      writeCheckpointV1 <- ZIO.service[WriteCheckpointV1]
      writeCheckpoint <- ZIO.service[WriteCheckpoint]
    } yield new CheckpointRepositoryImpl(writeCheckpointV1, writeCheckpoint)
  }
}

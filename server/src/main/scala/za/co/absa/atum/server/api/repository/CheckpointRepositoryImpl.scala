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

import za.co.absa.atum.model.dto.{CheckpointSubmitDTO, CheckpointQueryDTO, CheckpointQueryResultDTO}
import za.co.absa.atum.server.api.database.runs.functions.{GetPartitioningCheckpoints, WriteCheckpoint}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.fadb.exceptions.StatusException
import zio._

class CheckpointRepositoryImpl(
    writeCheckpointFn: WriteCheckpoint,
    getPartitioningCheckpointsFn: GetPartitioningCheckpoints
  )
  extends CheckpointRepository with BaseRepository {

  override def writeCheckpoint(checkpointDTO: CheckpointSubmitDTO): IO[DatabaseError, Either[StatusException, Unit]] = {
    dbCallWithStatus(writeCheckpointFn(checkpointDTO), "writeCheckpoint")
  }

  override def getPartitioningCheckpoints(partitioningName: CheckpointQueryDTO):
    IO[DatabaseError, Seq[CheckpointQueryResultDTO]] = {
    dbCall(getPartitioningCheckpointsFn(partitioningName).mapError(err => DatabaseError(err.getMessage)), "getPartitioningCheckpoints")
  }

}

object CheckpointRepositoryImpl {
  val layer: URLayer[WriteCheckpoint with GetPartitioningCheckpoints, CheckpointRepository] = ZLayer {
    for {
      writeCheckpoint <- ZIO.service[WriteCheckpoint]
      getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
    } yield new CheckpointRepositoryImpl(writeCheckpoint, getPartitioningCheckpoints)
  }
}

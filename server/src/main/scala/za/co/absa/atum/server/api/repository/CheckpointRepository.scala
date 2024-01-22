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
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpoint
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.fadb.exceptions.StatusException
import zio._
import zio.macros.accessible

@accessible
trait CheckpointRepository {
  def writeCheckpoint(checkpointDTO: CheckpointDTO): IO[DatabaseError, Either[StatusException, Unit]]
}

class CheckpointRepositoryImpl(writeCheckpointFn: WriteCheckpoint) extends CheckpointRepository {
  override def writeCheckpoint(checkpointDTO: CheckpointDTO): IO[DatabaseError, Either[StatusException, Unit]] = {
    writeCheckpointFn(checkpointDTO)
      .tap {
        case Left(statusException) =>
          ZIO.logError(s"Checkpoint write operation exception: (${statusException.status}) ${statusException.status}")
        case Right(_) =>
          ZIO.logDebug("Checkpoint successfully written to database.")
      }
      .mapError(error => DatabaseError(error.getMessage))
      .tapError(error => ZIO.logError(s"Failed to write checkpoint to database: ${error.message}"))
  }
}

object CheckpointRepositoryImpl {
  val layer: RLayer[WriteCheckpoint, CheckpointRepository] = ZLayer {
    for {
      writeCheckpoint <- ZIO.service[WriteCheckpoint]
    } yield new CheckpointRepositoryImpl(writeCheckpoint)
  }
}

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
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.model.PaginatedResult
import zio._
import zio.macros.accessible

import java.util.UUID

@accessible
trait CheckpointRepository {
  def writeCheckpoint(partitioningId: Long, checkpointV2DTO: CheckpointV2DTO): IO[DatabaseError, Unit]
  def getCheckpoint(
    partitioningId: Long,
    checkpointId: UUID,
    includeProperties: Boolean
  ): IO[DatabaseError, CheckpointV2DTO]
  def getPartitioningCheckpoints(
    partitioningId: Long,
    limit: Int,
    offset: Long,
    checkpointName: Option[String],
    includeProperties: Boolean
  ): IO[DatabaseError, PaginatedResult[CheckpointV2DTO]]
}

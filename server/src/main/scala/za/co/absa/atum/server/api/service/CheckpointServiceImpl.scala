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

package za.co.absa.atum.server.api.service

import za.co.absa.atum.model.dto.{CheckpointDTO, CheckpointV2DTO}
import za.co.absa.atum.model.envelopes.PaginatedResult
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.repository.CheckpointRepository
import zio._

import java.util.UUID

class CheckpointServiceImpl(checkpointRepository: CheckpointRepository) extends CheckpointService with BaseService {

  override def saveCheckpoint(checkpointDTO: CheckpointDTO): IO[ServiceError, Unit] = {
    repositoryCall(
      checkpointRepository.writeCheckpoint(checkpointDTO),
      "saveCheckpoint"
    )
  }

  override def saveCheckpointV2(partitioningId: Long, checkpointV2DTO: CheckpointV2DTO): IO[ServiceError, Unit] = {
    repositoryCall(
      checkpointRepository.writeCheckpointV2(partitioningId, checkpointV2DTO),
      "saveCheckpoint"
    )
  }

  override def getCheckpointV2(partitioningId: Long, checkpointId: UUID): IO[ServiceError, CheckpointV2DTO] = {
    repositoryCall(
      checkpointRepository.getCheckpointV2(partitioningId, checkpointId),
      "getCheckpoint"
    )
  }

  override def getPartitioningCheckpoints(
    partitioningId: Long,
    limit: Option[Int],
    offset: Option[Long],
    checkpointName: Option[String]
  ): IO[ServiceError, PaginatedResult[CheckpointV2DTO]] = {
    repositoryCall(
      checkpointRepository.getPartitioningCheckpoints(partitioningId, limit, offset, checkpointName),
      "getPartitioningCheckpoints"
    )
  }
}

object CheckpointServiceImpl {
  val layer: URLayer[CheckpointRepository, CheckpointService] = ZLayer {
    for {
      checkpointRepository <- ZIO.service[CheckpointRepository]
    } yield new CheckpointServiceImpl(checkpointRepository)
  }
}

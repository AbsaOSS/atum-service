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

import za.co.absa.atum.model.dto.CheckpointWithPartitioningDTO
import za.co.absa.atum.server.api.common.repository.{BaseRepository, CheckpointPropertiesEnricher}
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints.GetFlowCheckpointsArgs
import za.co.absa.atum.server.api.database.runs.functions.GetCheckpointProperties
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import za.co.absa.atum.server.model.PaginatedResult
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.atum.server.model.database.CheckpointItemWithPartitioningFromDB
import zio._
import zio.interop.catz.asyncInstance

class FlowRepositoryImpl(
  getFlowCheckpointsFn: GetFlowCheckpoints,
  override val getCheckpointPropertiesFn: GetCheckpointProperties
) extends FlowRepository with BaseRepository with CheckpointPropertiesEnricher {

  override def getFlowCheckpoints(
    flowId: Long,
    limit: Int,
    offset: Long,
    checkpointName: Option[String],
    includeProperties: Boolean
  ): IO[DatabaseError, PaginatedResult[CheckpointWithPartitioningDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getFlowCheckpointsFn(GetFlowCheckpointsArgs(flowId, limit, offset, checkpointName)),
      "getFlowCheckpoints"
    )
      .map(_.flatten)
      .flatMap { checkpointItems =>
        ZIO
          .fromEither(
            CheckpointItemWithPartitioningFromDB.groupAndConvertItemsToCheckpointWithPartitioningDTOs(checkpointItems)
          )
          .mapError(error => GeneralDatabaseError(error.getMessage))
          .flatMap { checkpoints =>
            val checkpointsF =
              if (includeProperties) ZIO.foreachPar(checkpoints)(enrichWithProperties)
              else ZIO.succeed(checkpoints)
            val resultCtor: Seq[CheckpointWithPartitioningDTO] => PaginatedResult[CheckpointWithPartitioningDTO] =
              if (checkpointItems.nonEmpty && checkpointItems.head.hasMore)
                ResultHasMore.apply[CheckpointWithPartitioningDTO]
              else ResultNoMore.apply[CheckpointWithPartitioningDTO]
            checkpointsF.map(resultCtor)
          }
      }
  }

}

object FlowRepositoryImpl {
  val layer: URLayer[GetCheckpointProperties with GetFlowCheckpoints, FlowRepositoryImpl] = ZLayer {
    for {
      getFlowCheckpointsV2 <- ZIO.service[GetFlowCheckpoints]
      getCheckpointProperties <- ZIO.service[GetCheckpointProperties]
    } yield new FlowRepositoryImpl(getFlowCheckpointsV2, getCheckpointProperties)
  }
}

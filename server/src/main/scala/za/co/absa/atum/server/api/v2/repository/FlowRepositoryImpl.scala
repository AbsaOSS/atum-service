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
import za.co.absa.atum.server.api.common.repository.BaseRepository
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints.GetFlowCheckpointsArgs
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import za.co.absa.atum.server.model.PaginatedResult
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.atum.server.model.database.CheckpointItemWithPartitioningFromDB
import zio._
import zio.interop.catz.asyncInstance

class FlowRepositoryImpl(getFlowCheckpointsFn: GetFlowCheckpoints) extends FlowRepository with BaseRepository {

  override def getFlowCheckpoints(
    flowId: Long,
    limit: Option[Int],
    offset: Option[Long],
    checkpointName: Option[String]
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
          .mapBoth(
            error => GeneralDatabaseError(error.getMessage),
            checkpoints =>
              if (checkpointItems.nonEmpty && checkpointItems.head.hasMore) ResultHasMore(checkpoints)
              else ResultNoMore(checkpoints)
          )
      }
  }

}

object FlowRepositoryImpl {
  val layer: URLayer[GetFlowCheckpoints, FlowRepository] = ZLayer {
    for {
      getFlowCheckpointsV2 <- ZIO.service[GetFlowCheckpoints]
    } yield new FlowRepositoryImpl(getFlowCheckpointsV2)
  }
}

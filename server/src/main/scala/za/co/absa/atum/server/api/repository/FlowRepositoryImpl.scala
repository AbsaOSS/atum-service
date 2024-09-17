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

import za.co.absa.atum.model.dto.{CheckpointQueryDTO, CheckpointV2DTO}
import za.co.absa.atum.server.api.database.flows.functions.{GetFlowCheckpoints, GetFlowCheckpointsV2}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.model.{CheckpointFromDB, CheckpointItemFromDB, ErrorResponse, PaginatedResult}
import za.co.absa.atum.server.model.SuccessResponse.PaginatedResponse
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpointsV2.GetFlowCheckpointsArgs
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import zio._
import zio.interop.catz.asyncInstance

class FlowRepositoryImpl(getFlowCheckpointsFn: GetFlowCheckpoints, getFlowCheckpointsV2Fn: GetFlowCheckpointsV2)
  extends FlowRepository with BaseRepository {

  override def getFlowCheckpoints(checkpointQueryDTO: CheckpointQueryDTO): IO[DatabaseError, Seq[CheckpointFromDB]] = {
    dbMultipleResultCallWithAggregatedStatus(getFlowCheckpointsFn(checkpointQueryDTO), "getFlowCheckpoints")
  }

  override def getFlowCheckpointsV2(
    partitioningId: Long,
    limit: Option[Int],
    offset: Option[Long],
    checkpointName: Option[String] = None,
   ): IO[DatabaseError, PaginatedResult[CheckpointV2DTO]] = {
      dbMultipleResultCallWithAggregatedStatus(
        getFlowCheckpointsV2Fn(GetFlowCheckpointsArgs(partitioningId, limit, offset, checkpointName)),
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

object FlowRepositoryImpl {
  val layer: URLayer[GetFlowCheckpoints with GetFlowCheckpointsV2, FlowRepository] = ZLayer {
    for {
      getFlowCheckpoints <- ZIO.service[GetFlowCheckpoints]
      getFlowCheckpointsV2 <- ZIO.service[GetFlowCheckpointsV2]
    } yield new FlowRepositoryImpl(getFlowCheckpoints, getFlowCheckpointsV2)
  }
}

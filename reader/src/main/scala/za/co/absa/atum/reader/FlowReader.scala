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

package za.co.absa.atum.reader

import sttp.client3.SttpBackend
import sttp.monad.MonadError
import sttp.monad.syntax._
import za.co.absa.atum.model.dto.{CheckpointWithPartitioningDTO, FlowDTO}
import za.co.absa.atum.model.envelopes.SuccessResponse.{PaginatedResponse, SingleSuccessResponse}
import za.co.absa.atum.model.types.basic.{AtumPartitions, PartitioningDTOOps}
import za.co.absa.atum.reader.core.RequestResult.{RequestPageResultOps, RequestResult}
import za.co.absa.atum.model.ApiPaths._
import za.co.absa.atum.model.types.{AtumPartitionsCheckpoint, Checkpoint}
import za.co.absa.atum.reader.core.{PartitioningIdProvider, Reader}
import za.co.absa.atum.reader.implicits.PaginatedResponseImplicits.PaginatedResponseMonadEnhancements
import za.co.absa.atum.reader.implicits.EitherImplicits.EitherMonadEnhancements
import za.co.absa.atum.reader.result.Page
import za.co.absa.atum.reader.server.ServerConfig
import za.co.absa.atum.reader.result.Page.PageRoller

/**
 * This class is a reader that reads data tight to a flow.
 * @param mainFlowPartitioning  - the partitioning of the main flow; renamed from ancestor's 'flowPartitioning'
 * @param serverConfig          - the Atum server configuration
 * @param backend               - sttp backend, that will be executing the requests
 * @param ev                    - using evidence based approach to ensure that the type F is a MonadError instead of using context
 *                              bounds, as it make the imports easier to follow
 * @tparam F                    - the effect type (e.g. Future, IO, Task, etc.)
 */
class FlowReader[F[_]](val mainFlowPartitioning: AtumPartitions)
                      (implicit serverConfig: ServerConfig, backend: SttpBackend[F, Any], ev: MonadError[F])
  extends Reader[F] with PartitioningIdProvider[F]{

  private def queryFlowId(mainPartitioningId: Long): F[RequestResult[Long]] = {
    val endpoint = s"/$Api/$V2/${V2Paths.Partitionings}/$mainPartitioningId/${V2Paths.MainFlow}"
    val queryResult = getQuery[SingleSuccessResponse[FlowDTO]](endpoint)
    queryResult.map{ result =>
      result.map(_.data.id)
    }
  }

  private def queryCheckpoints(flowId: Long,
                               checkpointName: Option[String],
                               pageSize: Int,
                               offset: Long): F[RequestResult[PaginatedResponse[CheckpointWithPartitioningDTO]]] = {
    val endpoint = s"/$Api/$V2/${V2Paths.Flows}/$flowId/${V2Paths.Checkpoints}"
    val params = Map(
      "limit" -> pageSize.toString,
      "offset" -> offset.toString
    ) ++ checkpointName.map("checkpoint-name" -> _)
    getQuery(endpoint, params)
  }

  private def checkpointMapper(data: CheckpointWithPartitioningDTO): AtumPartitionsCheckpoint = {
    val atumPartitions = data.partitioning.partitioning.toAtumPartitions
    val checkpoint = Checkpoint(data)
    AtumPartitionsCheckpoint(atumPartitions, checkpoint)
  }

  def getCheckpointDTOs(checkpointName: Option[String], pageSize: Int = 10, offset: Long = 0): F[RequestResult[Page[CheckpointWithPartitioningDTO, F]]] = {
    val pageRoller: PageRoller[CheckpointWithPartitioningDTO, F] = getCheckpointDTOs(checkpointName, _, _)

    for {
      mainPartitioningId <- partitioningId(mainFlowPartitioning)
      flowId <- mainPartitioningId.project(queryFlowId)
      checkpoints <- flowId.project(queryCheckpoints(_, checkpointName, pageSize, offset))
    } yield checkpoints.map(_.toPage(pageRoller))

  }

  def getCheckpoints(pageSize: Int = 10, offset: Long = 0): F[RequestResult[Page[AtumPartitionsCheckpoint, F]]] = {
    getCheckpointDTOs(None, pageSize, offset).map(_.pageMap(checkpointMapper))
  }

  def getCheckpointsOfName(name: String, pageSize: Int = 10, offset: Int = 0): F[RequestResult[Page[AtumPartitionsCheckpoint, F]]] = {
    getCheckpointDTOs(Some(name), pageSize, offset).map(_.pageMap(checkpointMapper))
  }
}

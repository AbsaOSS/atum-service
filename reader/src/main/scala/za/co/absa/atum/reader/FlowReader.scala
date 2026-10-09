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
import za.co.absa.atum.model.types.basic.AtumPartitions
import za.co.absa.atum.reader.core.RequestResult.RequestResult
import za.co.absa.atum.model.ApiPaths._
import za.co.absa.atum.reader.core.{PartitioningIdProvider, Reader}
import za.co.absa.atum.reader.requests.{CheckpointFilter, QueryParamNames}
import za.co.absa.atum.reader.server.ServerConfig

/**
 *  This class is a reader that reads data tight to a flow.
 *
 *  @param mainFlowPartitioning - the partitioning of the main flow; renamed from ancestor's 'flowPartitioning'
 *  @param serverConfig         - the Atum server configuration
 *  @param backend              - sttp backend, that will be executing the requests
 *  @tparam F                   - the effect type (e.g. Future, IO, Task, etc.)
 */
case class FlowReader[F[_]](mainFlowPartitioning: AtumPartitions)(implicit
  serverConfig: ServerConfig,
  backend: SttpBackend[F, Any],
  me: MonadError[F]
) extends Reader[F]
    with PartitioningIdProvider[F] {

  /**
   *  Function to retrieve a page of checkpoints belonging to the flow, optionally filtered.
   *  The checkpoints are ordered by their process start time, latest first.
   *
   *  @param pageSize          - the size of the page (record count) to be returned
   *  @param offset            - offset of the page (starting position)
   *  @param includeProperties - whether to include checkpoint properties in the response
   *  @param filter            - the checkpoints to return (by name and/or properties)
   *  @return                  - a page of checkpoints
   */
  def getCheckpointsPage(
    pageSize: Int = 10,
    offset: Long = 0,
    includeProperties: Boolean = false,
    filter: CheckpointFilter = CheckpointFilter.empty
  ): F[RequestResult[PaginatedResponse[CheckpointWithPartitioningDTO]]] = {
    for {
      flowIdOrError <- flowId
      checkpointsOrError <- mapRequestResultF(
        flowIdOrError,
        queryCheckpoints(_, filter, pageSize, offset, includeProperties)
      )
    } yield checkpointsOrError
  }

  /**
   *  Function to retrieve a page of checkpoints of the given name belonging to the flow.
   *  The checkpoints are ordered by their process start time, latest first.
   *
   *  @param checkpointName  - the name to filter with
   *  @param pageSize        - the size of the page (record count) to be returned
   *  @param offset          - offset of the page (starting position)
   *  @param includeProperties - whether to include checkpoint properties in the response
   *  @return                - a page of checkpoints
   */
  @deprecated("Use getCheckpointsPage with filter = CheckpointFilter(name = Some(checkpointName))", "0.9.0")
  def getCheckpointsOfNamePage(
    checkpointName: String,
    pageSize: Int = 10,
    offset: Long = 0,
    includeProperties: Boolean = false
  ): F[RequestResult[PaginatedResponse[CheckpointWithPartitioningDTO]]] = {
    getCheckpointsPage(pageSize, offset, includeProperties, CheckpointFilter(name = Some(checkpointName)))
  }

  /**
   *  Function to retrieve a page of checkpoints belonging to the flow that have all the given
   *  checkpoint properties (matching both property name and value).
   *  The checkpoints are ordered by their process start time, latest first.
   *
   *  @param checkpointProperties - the checkpoint properties (key-value pairs) to filter with;
   *                                a checkpoint is returned only if it has all of them
   *  @param pageSize             - the size of the page (record count) to be returned
   *  @param offset               - offset of the page (starting position)
   *  @param includeProperties    - whether to include checkpoint properties in the response
   *  @return                     - a page of checkpoints
   */
  @deprecated("Use getCheckpointsPage with filter = CheckpointFilter(properties = ...)", "0.9.0")
  def getCheckpointsByPropertiesPage(
    checkpointProperties: Map[String, String],
    pageSize: Int = 10,
    offset: Long = 0,
    includeProperties: Boolean = false
  ): F[RequestResult[PaginatedResponse[CheckpointWithPartitioningDTO]]] = {
    val properties = checkpointProperties.map { case (propertyName, value) => propertyName -> Set(value) }
    getCheckpointsPage(pageSize, offset, includeProperties, CheckpointFilter(properties = properties))
  }

  /**
   *  Returns the author of the partitioning identifying the flow.
   *
   *  @return - the author of the main flow partitioning
   */
  def getAuthor: F[RequestResult[String]] = partitioningAuthor(mainFlowPartitioning)

  private def queryFlowId(mainPartitioningId: Long): F[RequestResult[Long]] = {
    val endpoint = s"/$Api/$V2/${V2Paths.Partitionings}/$mainPartitioningId/${V2Paths.MainFlow}"
    val queryResult = getQuery[SingleSuccessResponse[FlowDTO]](endpoint)
    queryResult.map { result =>
      result.map(_.data.id)
    }
  }

  private def flowId: F[RequestResult[Long]] = {
    for {
      mainPartitioningIdOrError <- partitioningId(mainFlowPartitioning)
      flowIdOrError <- mapRequestResultF(mainPartitioningIdOrError, queryFlowId)
    } yield flowIdOrError
  }

  private def queryCheckpoints(
    flowId: Long,
    filter: CheckpointFilter,
    limit: Int,
    offset: Long,
    includeProperties: Boolean
  ): F[RequestResult[PaginatedResponse[CheckpointWithPartitioningDTO]]] = {
    val endpoint = s"/$Api/$V2/${V2Paths.Flows}/$flowId/${V2Paths.Checkpoints}"
    val params = Map(
      QueryParamNames.Limit -> limit.toString,
      QueryParamNames.Offset -> offset.toString,
      QueryParamNames.IncludeProperties -> includeProperties.toString
    ) ++ filter.toQueryParams
    getQuery(endpoint, params)
  }

}

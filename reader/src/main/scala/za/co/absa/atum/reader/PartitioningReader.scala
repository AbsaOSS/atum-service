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
import za.co.absa.atum.model.ApiPaths.{Api, V2, V2Paths}
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataItemDTO, AdditionalDataItemV2DTO, CheckpointV2DTO}
import za.co.absa.atum.model.envelopes.SuccessResponse.{MultiSuccessResponse, PaginatedResponse, SingleSuccessResponse}
import za.co.absa.atum.model.types.basic.AtumPartitions
import za.co.absa.atum.reader.core.RequestResult.RequestResult
import za.co.absa.atum.reader.core.{PartitioningIdProvider, Reader}
import za.co.absa.atum.reader.requests.QueryParamNames
import za.co.absa.atum.reader.server.ServerConfig

/**
 *  @param partitioning  - the Atum partitions to read the information from
 *  @param serverConfig  - the Atum server configuration
 *  @param backend       - sttp backend, that will be executing the requests
 *  @tparam F            - the effect type (e.g. Future, IO, Task, etc.)
 */
case class PartitioningReader[F[_]](partitioning: AtumPartitions)(implicit
  serverConfig: ServerConfig,
  backend: SttpBackend[F, Any],
  me: MonadError[F]
) extends Reader[F]
    with PartitioningIdProvider[F] {

  /**
   *  Function to retrieve a page of checkpoints belonging to the partitioning.
   *  The checkpoints are ordered by their creation order.
   *
   *  @param pageSize  - the size of the page (record count) to be returned
   *  @param offset    - offset of the page (starting position)
   *  @param includeProperties - whether to include checkpoint properties in the response
   *  @return          - a page of checkpoints
   */
  def getCheckpointsPage(
    pageSize: Int = 10,
    offset: Long = 0,
    includeProperties: Boolean = false
  ): F[RequestResult[PaginatedResponse[CheckpointV2DTO]]] = {
    for {
      partitioningIdOrError <- partitioningId(partitioning)
      checkpointsOrError <- mapRequestResultF(
        partitioningIdOrError,
        queryCheckpoints(_, None, pageSize, offset, includeProperties)
      )
    } yield checkpointsOrError
  }

  /**
   *  Function to retrieve a page of checkpoints of the given name belonging to the partitioning. (While the usual logic
   *  would suggest, there would be only one checkpoint of a name, nothing prevents to have checkpoints of the same name;
   *  also during reprocessing the checkpoints might multiply.)
   *  The checkpoints are ordered by their creation order.
   *
   *  @param checkpointName    - the name to filter with
   *  @param pageSize          - the size of the page (record count) to be returned
   *  @param offset            - offset of the page (starting position)
   *  @param includeProperties - whether to include checkpoint properties in the response
   *  @return                  - a page of checkpoints
   */
  def getCheckpointsOfNamePage(
    checkpointName: String,
    pageSize: Int = 10,
    offset: Long = 0,
    includeProperties: Boolean = false
  ): F[RequestResult[PaginatedResponse[CheckpointV2DTO]]] = {
    for {
      partitioningIdOrError <- partitioningId(partitioning)
      checkpointsOrError <- mapRequestResultF(
        partitioningIdOrError,
        queryCheckpoints(_, Some(checkpointName), pageSize, offset, includeProperties)
      )
    } yield checkpointsOrError
  }

  /**
   *  Returns the additional data associated with the partitioning
   *
   *  @return - the additional data as they were save for the partitioning
   */
  def getAdditionalData: F[RequestResult[AdditionalDataDTO]] = {
//  def getAdditionalData: F[RequestResult[Seq[AdditionalDataItemV2DTO]]] = {
    for {
      partitioningIdOrError <- partitioningId(partitioning)
      additionalDataOrError <- mapRequestResultF(partitioningIdOrError, queryAdditionalData)
    } yield additionalDataOrError//.map(_.data)
      .map { multiSuccessResponse =>
      val ad = AdditionalDataDTO(
        multiSuccessResponse.data.map { itemV2DTO =>
          itemV2DTO.value match {
            case Some(value) => (itemV2DTO.key, Some(AdditionalDataItemDTO(value, itemV2DTO.author)))
            case None => (itemV2DTO.key, None)
          }
        }
      )
      println(ad)
      ad
    }
  }

  private def queryCheckpoints(
    partitioningId: Long,
    checkpointName: Option[String],
    limit: Int,
    offset: Long,
    includeProperties: Boolean
  ): F[RequestResult[PaginatedResponse[CheckpointV2DTO]]] = {
    val endpoint = s"/$Api/$V2/${V2Paths.Partitionings}/$partitioningId/${V2Paths.Checkpoints}"
    val params = Map(
      QueryParamNames.Limit -> limit.toString,
      QueryParamNames.Offset -> offset.toString,
      QueryParamNames.IncludeProperties -> includeProperties.toString
    ) ++ checkpointName.map(QueryParamNames.CheckpointName -> _)
    getQuery(endpoint, params)
  }

  private def queryAdditionalData(partitioningId: Long): F[RequestResult[MultiSuccessResponse[AdditionalDataItemV2DTO]]] = {
    val endpoint = s"$Api/$V2/${V2Paths.Partitionings}/$partitioningId/${V2Paths.AdditionalData}"
    getQuery(endpoint)
  }
}

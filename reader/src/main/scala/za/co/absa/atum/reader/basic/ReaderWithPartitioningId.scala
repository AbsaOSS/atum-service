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

package za.co.absa.atum.reader.basic

import sttp.client3.SttpBackend
import sttp.monad.MonadError
import sttp.monad.syntax._
import za.co.absa.atum.model.dto.PartitioningWithIdDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.types.basic.AtumPartitions
import za.co.absa.atum.model.types.basic.AtumPartitionsOps
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax
import za.co.absa.atum.reader.basic.RequestResult.RequestResult
import za.co.absa.atum.reader.server.ServerConfig

abstract class ReaderWithPartitioningId[F[_]: MonadError](implicit serverConfig: ServerConfig, backend: SttpBackend[F, Any])
  extends Reader[F] {
  def partitioning: AtumPartitions

  protected def partitioningId(): F[RequestResult[Long]] = {
    val encodedPartitioning = partitioning.toPartitioningDTO.asBase64EncodedJsonString
    val queryResult = getQuery[SingleSuccessResponse[PartitioningWithIdDTO]]("/api/v2/partitionings", Map("partitioning" -> encodedPartitioning))
    queryResult.map{result =>
      result.map(_.data.id)
    }
  }
}

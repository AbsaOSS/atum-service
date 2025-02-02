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

package za.co.absa.atum.reader.core

import sttp.monad.MonadError
import sttp.monad.syntax._
import za.co.absa.atum.model.ApiPaths._
import za.co.absa.atum.model.dto.PartitioningWithIdDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.types.basic.AtumPartitions
import za.co.absa.atum.model.types.basic.AtumPartitionsOps
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax
import RequestResult.RequestResult

trait PartitioningIdProvider[F[_]] {self: Reader[F] =>
  def partitioningId(partitioning: AtumPartitions)(implicit monad: MonadError[F]): F[RequestResult[Long]] = {
    val encodedPartitioning = partitioning.toPartitioningDTO.asBase64EncodedJsonString
    val queryResult = getQuery[SingleSuccessResponse[PartitioningWithIdDTO]](s"/$Api/$V2/${V2Paths.Partitionings}", Map("partitioning" -> encodedPartitioning))
    queryResult.map{ result =>
      result.map(_.data.id)
    }
  }
}

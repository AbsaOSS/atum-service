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

package za.co.absa.atum.model.dto

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
 *  Request payload for linking an existing partitioning under one or more parent partitionings.
 *  Handled by POST /api/v2/partitionings/{partitioningId}/parents.
 *
 *  @param parentPartitioningIds   one or more parent partitioning ids
 *  @param author                  who is establishing this parentage link
 *  @param copyMeasureDefinitions  whether to copy measure definitions from parents
 */
case class PartitioningParentsSubmitDTO(
  parentPartitioningIds: Seq[Long],
  author: String,
  copyMeasureDefinitions: Option[Boolean] = None
)

object PartitioningParentsSubmitDTO {
  implicit def encoder: Encoder[PartitioningParentsSubmitDTO] = deriveEncoder
  implicit def decoder: Decoder[PartitioningParentsSubmitDTO] = deriveDecoder
}

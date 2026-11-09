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

import java.time.ZonedDateTime

/**
 * A direct (immediate) parent of a partitioning, with the metadata of the parent-child link.
 * Returned by GET /api/v2/partitionings/{id}/parents.
 *
 * @param id           parent partitioning id
 * @param partitioning the parent's partitioning (ordered key-value pairs)
 * @param author       who created the parent partitioning
 * @param linkedBy     who established the parent-child link
 * @param linkedAt     when the parent-child link was established
 */
case class ParentPartitioningDTO(
  id: Long,
  partitioning: PartitioningDTO,
  author: String,
  linkedBy: Option[String] = None,
  linkedAt: Option[ZonedDateTime] = None
)

object ParentPartitioningDTO {
  implicit def encoder: Encoder[ParentPartitioningDTO] = deriveEncoder
  implicit def decoder: Decoder[ParentPartitioningDTO] = deriveDecoder
}

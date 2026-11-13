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
 *  A direct edge connecting a parent partitioning to a child partitioning.
 *
 *  @param parentPartitioningId ID of the parent (upstream)
 *  @param childPartitioningId  ID of the child (downstream)
 *  @param linkedBy             who created the relationship
 *  @param linkedAt             when the relationship was created
 */
case class LineageEdgeDTO(
  parentPartitioningId: Long,
  childPartitioningId: Long,
  linkedBy: Option[String] = None,
  linkedAt: Option[ZonedDateTime] = None
)

object LineageEdgeDTO {
  implicit def encoder: Encoder[LineageEdgeDTO] = deriveEncoder
  implicit def decoder: Decoder[LineageEdgeDTO] = deriveDecoder
}

/**
 *  The full lineage sub-graph centered on a primary partitioning.
 *  Returned by GET /api/v2/partitionings/{id}/lineage.
 *
 *  @param primaryPartitioningId the root or focal partitioning ID requested
 *  @param depth                 traversal depth evaluated
 *  @param direction             traversal direction ('both', 'upstream', or 'downstream')
 *  @param nodes                 all partitioning nodes present in the sub-graph
 *  @param edges                 all direct edges interconnecting the nodes
 */
case class LineageGraphDTO(
  primaryPartitioningId: Long,
  depth: Int,
  direction: String,
  nodes: Seq[PartitioningWithIdDTO],
  edges: Seq[LineageEdgeDTO]
)

object LineageGraphDTO {
  implicit def encoder: Encoder[LineageGraphDTO] = deriveEncoder
  implicit def decoder: Decoder[LineageGraphDTO] = deriveDecoder
}

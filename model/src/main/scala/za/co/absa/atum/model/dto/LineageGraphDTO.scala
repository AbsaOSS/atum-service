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
 *  The full lineage sub-graph centered on a primary partitioning.
 *  Returned by GET /api/v2/partitionings/{id}/lineage.
 *
 *  @param primaryPartitioningId the root or focal partitioning ID requested
 *  @param depth                 maximum traversal depth requested
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

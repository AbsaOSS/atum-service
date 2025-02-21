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

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.ZonedDateTime
import java.util.UUID

case class CheckpointWithPartitioningDTO(
  id: UUID,
  name: String,
  author: String,
  measuredByAtumAgent: Boolean = false,
  processStartTime: ZonedDateTime,
  processEndTime: Option[ZonedDateTime],
  measurements: Set[MeasurementDTO],
  partitioning: PartitioningWithIdDTO
)

object CheckpointWithPartitioningDTO {

  implicit val decodeCheckpointWithPartitioningDTO: Decoder[CheckpointWithPartitioningDTO] = deriveDecoder
  implicit val encodeCheckpointWithPartitioningDTO: Encoder[CheckpointWithPartitioningDTO] = deriveEncoder

}

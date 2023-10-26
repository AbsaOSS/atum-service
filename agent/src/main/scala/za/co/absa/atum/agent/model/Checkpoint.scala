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

package za.co.absa.atum.agent.model

import za.co.absa.atum.agent.AtumContext.AtumPartitions
import za.co.absa.atum.model.dto.CheckpointDTO

import java.time.ZonedDateTime
import java.util.UUID

case class Checkpoint(
  name: String,
  author: String,
  measuredByAtumAgent: Boolean = false,
  atumPartitions: AtumPartitions,
  processStartTime: ZonedDateTime,
  processEndTime: Option[ZonedDateTime],
  measurements: Seq[Measurement]
) {
  private [agent] def toCheckpointDTO: CheckpointDTO = {
    val measurementDTOs = measurements.map(MeasurementBuilder.buildMeasurementDTO)

    CheckpointDTO(
      id = UUID.randomUUID(),
      name = name,
      author = author,
      measuredByAtumAgent = measuredByAtumAgent,
      partitioning = AtumPartitions.toSeqPartitionDTO(atumPartitions),
      processStartTime = processStartTime,
      processEndTime = processEndTime,
      measurements = measurementDTOs
    )
  }
}

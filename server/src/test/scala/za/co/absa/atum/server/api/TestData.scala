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

package za.co.absa.atum.server.api

import za.co.absa.atum.model.dto.{AdditionalDataDTO, AtumContextDTO, CheckpointDTO, MeasureDTO, PartitioningSubmitDTO}

import java.time.ZonedDateTime
import java.util.UUID

trait TestData {

  protected val partitioningSubmitDTO1: PartitioningSubmitDTO = PartitioningSubmitDTO(
    partitioning = Seq.empty,
    parentPartitioning = None,
    authorIfNew = ""
  )
  protected val partitioningSubmitDTO2: PartitioningSubmitDTO =
    partitioningSubmitDTO1.copy(authorIfNew = "differentAuthor")

  protected val partitioningSubmitDTO3: PartitioningSubmitDTO =
    partitioningSubmitDTO1.copy(authorIfNew = "yetAnotherAuthor")

  protected val checkpointDTO1: CheckpointDTO = CheckpointDTO(
    id = UUID.randomUUID(),
    name = "name",
    author = "author",
    partitioning = Seq.empty,
    processStartTime = ZonedDateTime.now(),
    processEndTime = None,
    measurements = Seq.empty
  )
  protected val checkpointDTO2: CheckpointDTO = checkpointDTO1.copy(id = UUID.randomUUID())

  protected val checkpointDTO3: CheckpointDTO = checkpointDTO1.copy(id = UUID.randomUUID())

  protected def createAtumContextDTO(partitioningSubmitDTO: PartitioningSubmitDTO): AtumContextDTO = {
    val measures: Set[MeasureDTO] = Set(MeasureDTO("count", Seq("*")))
    val additionalData = AdditionalDataDTO(additionalData = Map.empty)
    AtumContextDTO(partitioningSubmitDTO.partitioning, measures, additionalData)
  }

}

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

package za.co.absa.atum.agent.reader

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataItemDTO, CheckpointV2DTO}

import java.util.UUID
import java.time.ZonedDateTime

class PartitioningReaderImplUnitTest extends AnyFlatSpec with Matchers {

  "PartitioningReaderImpl" should "return the correct additional data" in {
    val additionalDataItem = AdditionalDataItemDTO(Some("value"), "author")
    val additionalData = Some(AdditionalDataDTO(Map("key" -> Some(additionalDataItem))))
    val checkpoints = List(CheckpointV2DTO(
      UUID.randomUUID(),
      "checkpoint1",
      "author1",
      measuredByAtumAgent = true,
      ZonedDateTime.now(),
      Some(ZonedDateTime.now().plusHours(1)),
      Set.empty
    ))
    val reader = new PartitioningReaderImpl(additionalData, checkpoints)

    reader.getAdditionalData shouldEqual additionalData
  }

  it should "return the correct checkpoints" in {
    val additionalDataItem = AdditionalDataItemDTO(Some("value"), "author")
    val additionalData = Some(AdditionalDataDTO(Map("key" -> Some(additionalDataItem))))
    val checkpoints = List(CheckpointV2DTO(
      UUID.randomUUID(),
      "checkpoint1",
      "author1",
      measuredByAtumAgent = true,
      ZonedDateTime.now(),
      Some(ZonedDateTime.now().plusHours(1)),
      Set.empty
    ))
    val reader = new PartitioningReaderImpl(additionalData, checkpoints)

    reader.getCheckpoints shouldEqual checkpoints
  }
}

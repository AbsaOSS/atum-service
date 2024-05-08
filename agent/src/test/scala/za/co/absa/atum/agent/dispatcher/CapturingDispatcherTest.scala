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

package za.co.absa.atum.agent.dispatcher

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import za.co.absa.atum.model.dto.{AtumContextDTO, CheckpointSubmitDTO, PartitionDTO, PartitioningDTO, PartitioningSubmitDTO}

import java.time.ZonedDateTime
import java.util.UUID
import com.github.dwickern.macros.NameOf._
import za.co.absa.atum.agent.dispatcher.CapturingDispatcher.CapturedCall

class CapturingDispatcherTest extends AnyWordSpec with Matchers {
  private val DefaultProcessStartTime = ZonedDateTime.parse("2024-03-11T00:00:00Z")

  private def config(captureLimit: Int): Config = {
    val emptyCfg = ConfigFactory.empty()
    val value = ConfigValueFactory.fromAnyRef(captureLimit)
    emptyCfg.withValue("atum.dispatcher.capture.capture-limit", value)
  }

  private def createCheckpoint(partition: PartitioningDTO): CheckpointSubmitDTO =
    CheckpointSubmitDTO(
      id = UUID.randomUUID(),
      name = "name",
      author = "author",
      partitioning = partition,
      processStartTime = DefaultProcessStartTime,
      processEndTime = None,
      measurements = Set.empty
    )

  private def createPartition(kvs: (String, String)*): PartitioningDTO = {
    kvs.map { case (k, v) => PartitionDTO(k, v) }
  }

  private def createPartitionSubmit(partition: PartitioningDTO, parent: Option[PartitioningDTO] = None): PartitioningSubmitDTO = {
    PartitioningSubmitDTO(
      partitioning = partition,
      parentPartitioning = parent,
      authorIfNew = "another author"
    )
  }


  "CaptureDispatcher" should {
    val dispatcher = new CapturingDispatcher(config(3))
    val partitioning1 = createPartition("k1" -> "v1")
    val partitioning2 = createPartition("k1" -> "v1", "k2" -> "v2")
    val partitioning1Submit = createPartitionSubmit(partitioning1)
    val partitioning2Submit = createPartitionSubmit(partitioning2, Some(partitioning1))
    val checkpoint1 = createCheckpoint(partitioning1)
    val checkpoint2 = createCheckpoint(partitioning2)

    "captures no more than limit" in {
      dispatcher.createPartitioning(partitioning1Submit)
      dispatcher.saveCheckpoint(checkpoint1)
      dispatcher.createPartitioning(partitioning2Submit)
      dispatcher.saveCheckpoint(checkpoint2)

      dispatcher.captures.size shouldBe 3
    }

    "verifies presence of captures" in {
      val expectedResult = AtumContextDTO(partitioning2)
      dispatcher.contains(nameOf(dispatcher.createPartitioning _), partitioning2Submit, expectedResult) shouldBe true
      dispatcher.contains(nameOf(dispatcher.createPartitioning _), partitioning2Submit) shouldBe true
      dispatcher.contains(nameOf(dispatcher.saveCheckpoint _)) shouldBe true
    }

    "verifies absence of captures" in {
      val unexpectedResult = AtumContextDTO(partitioning1)
      dispatcher.contains(nameOf(dispatcher.createPartitioning _), partitioning2Submit, unexpectedResult) shouldBe false
      dispatcher.contains(nameOf(dispatcher.createPartitioning _), partitioning1Submit) shouldBe false
      dispatcher.contains(nameOf(dispatcher.saveAdditionalData _)) shouldBe false
    }

    "lists all captures" in {
      val expected = List(
        CapturedCall(nameOf(dispatcher.saveCheckpoint _), checkpoint1, ()),
        CapturedCall(nameOf(dispatcher.createPartitioning _), partitioning2Submit, AtumContextDTO(partitioning2)),
        CapturedCall(nameOf(dispatcher.saveCheckpoint _), checkpoint2, ())
      )
      dispatcher.captures shouldBe expected
    }

    "removes all captures with clear" in {
      dispatcher.captures.size shouldBe 3
      dispatcher.clear()
      dispatcher.captures.size shouldBe 0
    }
  }

}

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
import za.co.absa.atum.agent.model.PartitioningCreateRequest
import za.co.absa.atum.model.dto.{AtumContextDTO, CheckpointDTO, PartitionDTO, PartitioningDTO}

import java.time.ZonedDateTime
import java.util.UUID
import com.github.dwickern.macros.NameOf._
import za.co.absa.atum.agent.dispatcher.CapturingDispatcher.CapturedCall

class CapturingDispatcherUnitTests extends AnyWordSpec with Matchers {
  private val DefaultProcessStartTime = ZonedDateTime.parse("2024-03-11T00:00:00Z")

  private def config(captureLimit: Int): Config = {
    val emptyCfg = ConfigFactory.empty()
    val value = ConfigValueFactory.fromAnyRef(captureLimit)
    emptyCfg.withValue("atum.dispatcher.capture.capture-limit", value)
  }

  private def createCheckpoint(partition: PartitioningDTO): CheckpointDTO =
    CheckpointDTO(
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

  private def createPartitionSubmit(
    partition: PartitioningDTO,
    parent: Option[PartitioningDTO] = None
  ): PartitioningCreateRequest = {
    PartitioningCreateRequest(
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
      dispatcher.contains(nameOf(dispatcher.updateAdditionalData _)) shouldBe false
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

    "captures the PartitioningCreateRequest carrier as the createPartitioning input" in {
      val freshDispatcher = new CapturingDispatcher(config(10))
      val request =
        PartitioningCreateRequest(partitioning2, Seq(partitioning1, createPartition("k9" -> "v9")), "author-x")

      val result = freshDispatcher.createPartitioning(request)

      result shouldBe AtumContextDTO(partitioning2)
      freshDispatcher.captures.size shouldBe 1
      val captured = freshDispatcher.captures.head
      captured.functionName shouldBe nameOf(freshDispatcher.createPartitioning _)
      captured.input.asInstanceOf[AnyRef] shouldBe a[PartitioningCreateRequest]
      captured.input.asInstanceOf[PartitioningCreateRequest].parentPartitionings should have size 2
      captured.input.asInstanceOf[PartitioningCreateRequest].authorIfNew shouldBe "author-x"
      freshDispatcher.contains(nameOf(freshDispatcher.createPartitioning _), request) shouldBe true
      freshDispatcher.contains(nameOf(freshDispatcher.createPartitioning _), request, result) shouldBe true
    }

    "carries the copyMeasureDefinitions flag through createPartitioning" in {
      val freshDispatcher = new CapturingDispatcher(config(10))
      val optedIn =
        PartitioningCreateRequest(partitioning2, Seq(partitioning1), "author-x", copyMeasureDefinitions = true)
      val viaFactory = PartitioningCreateRequest(partitioning2, Some(partitioning1), "author-x")

      freshDispatcher.createPartitioning(optedIn)
      freshDispatcher.createPartitioning(viaFactory)

      freshDispatcher.captures.size shouldBe 2
      freshDispatcher.captures.head.input.asInstanceOf[PartitioningCreateRequest].copyMeasureDefinitions shouldBe true
      freshDispatcher.captures(1).input.asInstanceOf[PartitioningCreateRequest].copyMeasureDefinitions shouldBe false
      viaFactory.copyMeasureDefinitions shouldBe false
    }
  }

}

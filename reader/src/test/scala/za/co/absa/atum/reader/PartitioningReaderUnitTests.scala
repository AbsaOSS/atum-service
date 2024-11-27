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

package za.co.absa.atum.reader

import cats.Id
import org.mockito.MockitoSugar
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataItemDTO, CheckpointV2DTO}
import za.co.absa.atum.model.types.BasicTypes.{AdditionalData, AtumPartitions}
import za.co.absa.atum.model.types.Checkpoint

import java.time.ZonedDateTime
import java.util.UUID
import cats.Monad
import scala.collection.immutable.ListMap
//import za.co.absa.atum.reader.server.GenericServerConnection.Dispatcher

class PartitioningReaderUnitTests extends AnyFunSuiteLike with Matchers with MockitoSugar {

  private implicit val idMonad: Monad[Id] = Monad[Id]

  trait TestContext {
    val partitioning: AtumPartitions = ListMap("key1" -> "value1", "key2" -> "value2")
    implicit val dispatcher: Dispatcher = mock[Dispatcher]
    val reader: PartitioningReader[Id] = new PartitioningReader[Id](partitioning)
  }

  protected val additionalDataDTO1: AdditionalDataDTO = AdditionalDataDTO(
    Map(
      "key1" -> Some(AdditionalDataItemDTO(Some("value1"), "author")),
      "key2" -> None,
      "key3" -> Some(AdditionalDataItemDTO(Some("value3"), "author"))
    ))

  test("getAdditionalData should fetch and transform additional data correctly") {
    new TestContext {
      when(dispatcher.getAdditionalData(partitioning)).thenReturn(additionalDataDTO1)

      val result: Id[AdditionalData] = reader.getAdditionalData

      Map(
        "key1" -> Some("value1"),
        "key2" -> None,
        "key3" -> Some("value3")
      ) shouldEqual result
    }
  }

  test("getCheckpoints should fetch and transform checkpoints correctly") {
    new TestContext {
      val checkpointsDTO: Seq[CheckpointV2DTO] = Seq(
        CheckpointV2DTO(
          id = UUID.randomUUID(),
          name = "checkpoint1",
          author = "author1",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.now(),
          processEndTime = Some(ZonedDateTime.now().plusHours(1)),
          measurements = Set.empty
        ),
        CheckpointV2DTO(
          id = UUID.randomUUID(),
          name = "checkpoint2",
          author = "author2",
          measuredByAtumAgent = false,
          processStartTime = ZonedDateTime.now().minusDays(1),
          processEndTime = None,
          measurements = Set.empty
        )
      )

      when(dispatcher.getCheckpoints(partitioning, Some(10), Some(0L), Some("checkpoint1"))).thenReturn(checkpointsDTO)

      val result: Id[List[Checkpoint]] = reader.getCheckpoints(Some(10), Some(0L), Some("checkpoint1"))

      result shouldEqual checkpointsDTO.map { dto =>
        Checkpoint(
          id = dto.id.toString,
          name = dto.name,
          author = dto.author,
          measuredByAtumAgent = dto.measuredByAtumAgent,
          processStartTime = dto.processStartTime,
          processEndTime = dto.processEndTime,
          measurements = dto.measurements
        )
      }.toList
    }
  }

}

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

package za.co.absa.atum.server.model

import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.server.api.TestData

import java.time.ZonedDateTime
import java.util.UUID

class CheckpointItemWithPartitioningFromDBUnitTests extends AnyFunSuiteLike with TestData {

  test("groupAndConvertItemsToCheckpointWithPartitioningDTOs should group by idCheckpoint and sort by checkpointStartTime in descending order") {
    val checkpointItem1 = CheckpointItemWithPartitioningFromDB(
      idCheckpoint = UUID.randomUUID(),
      checkpointName = "checkpoint1",
      author = "author1",
      measuredByAtumAgent = true,
      measureName = "measure1",
      measuredColumns = Seq("col1"),
      measurementValue = measurementValue1,
      checkpointStartTime = ZonedDateTime.now().minusDays(1),
      checkpointEndTime = Some(ZonedDateTime.now()),
      idPartitioning = 1L,
      partitioning = partitioningAsJson,
      partitioningAuthor = "author1",
      hasMore = false
    )

    val checkpointItem2 = CheckpointItemWithPartitioningFromDB(
      idCheckpoint = UUID.randomUUID(),
      checkpointName = "checkpoint2",
      author = "author2",
      measuredByAtumAgent = false,
      measureName = "measure2",
      measuredColumns = Seq("col2"),
      measurementValue = measurementValue1,
      checkpointStartTime = ZonedDateTime.now(),
      checkpointEndTime = Some(ZonedDateTime.now().plusDays(1)),
      idPartitioning = 2L,
      partitioning = partitioningAsJson,
      partitioningAuthor = "author2",
      hasMore = true
    )

    val checkpointItems = Seq(checkpointItem1, checkpointItem2)

    val result = CheckpointItemWithPartitioningFromDB.groupAndConvertItemsToCheckpointWithPartitioningDTOs(checkpointItems)

    assert(result.isRight)
    val checkpoints = result.toOption.get

    assert(checkpoints.size == 2)
    assert(checkpoints.head.processStartTime.isAfter(checkpoints(1).processStartTime))
  }
}

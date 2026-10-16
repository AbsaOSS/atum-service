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

package za.co.absa.atum.server.model.database

import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.server.api.TestData

import java.time.ZonedDateTime
import java.util.UUID

class CheckpointItemWithPartitioningFromDBUnitTests extends AnyFunSuiteLike with TestData {

  test("groupAndConvertItemsToCheckpointWithPartitioningDTOs should group by idCheckpoint and keep the order of the DB rows") {
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

    // a second measure of the first checkpoint, i.e. another DB row of the same checkpoint
    val checkpointItem1OtherMeasure = checkpointItem1.copy(measureName = "measure3", measuredColumns = Seq("col3"))

    // earliest first, as returned by the DB for latest-first = false
    val earliestFirst = CheckpointItemWithPartitioningFromDB
      .groupAndConvertItemsToCheckpointWithPartitioningDTOs(Seq(checkpointItem1, checkpointItem1OtherMeasure, checkpointItem2))
    assert(earliestFirst.isRight)
    assert(earliestFirst.toOption.get.map(_.id) == Seq(checkpointItem1.idCheckpoint, checkpointItem2.idCheckpoint))
    assert(earliestFirst.toOption.get.head.measurements.size == 2)

    // latest first, as returned by the DB by default
    val latestFirst = CheckpointItemWithPartitioningFromDB
      .groupAndConvertItemsToCheckpointWithPartitioningDTOs(Seq(checkpointItem2, checkpointItem1, checkpointItem1OtherMeasure))
    assert(latestFirst.isRight)
    assert(latestFirst.toOption.get.map(_.id) == Seq(checkpointItem2.idCheckpoint, checkpointItem1.idCheckpoint))
  }
}

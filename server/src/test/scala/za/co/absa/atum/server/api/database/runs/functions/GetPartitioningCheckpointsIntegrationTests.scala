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

package za.co.absa.atum.server.api.database.runs.functions

import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningCheckpoints.GetPartitioningCheckpointsArgs
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpointV2.WriteCheckpointArgs
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio.interop.catz.asyncInstance
import zio.{Scope, ZIO}
import zio.test._

import java.time.ZonedDateTime
import java.util.UUID

object GetPartitioningCheckpointsIntegrationTests extends ConfigProviderTest {

  private val checkpointName = "Data written"
  private val firstStartTime = ZonedDateTime.parse("2026-01-01T00:00:00Z")

  private def createPartitioning: ZIO[CreatePartitioning, Throwable, Long] =
    ZIO
      .serviceWithZIO[CreatePartitioning](
        _(PartitioningSubmitV2DTO(Seq(PartitionDTO("key", UUID.randomUUID().toString)), None, "author"))
      )
      .flatMap(ZIO.fromEither(_))
      .map(_.data)

  private def writeCheckpoint(
    partitioningId: Long,
    name: String,
    executionId: Option[String],
    startMinute: Long
  ): ZIO[WriteCheckpointV2, Throwable, UUID] = {
    val checkpoint = CheckpointV2DTO(
      id = UUID.randomUUID(),
      name = name,
      author = "author",
      processStartTime = firstStartTime.plusMinutes(startMinute),
      processEndTime = None,
      measurements = Set(
        MeasurementDTO(MeasureDTO("count", Seq("*")), MeasureResultDTO(TypedValue("1", ResultValueType.LongValue)))
      ),
      properties = executionId.map(id => Map("executionID" -> id))
    )
    ZIO
      .serviceWithZIO[WriteCheckpointV2](_(WriteCheckpointArgs(partitioningId, checkpoint)))
      .flatMap(ZIO.fromEither(_))
      .as(checkpoint.id)
  }

  // returns the IDs of the checkpoints on the page, in the order received, and the has-more flag
  private def getPage(
    args: GetPartitioningCheckpointsArgs
  ): ZIO[GetPartitioningCheckpoints, Throwable, (Seq[UUID], Boolean)] =
    ZIO.serviceWithZIO[GetPartitioningCheckpoints](_(args)).flatMap(ZIO.fromEither(_)).map { rows =>
      val items = rows.flatMap(_.data)
      (items.map(_.idCheckpoint), items.exists(_.hasMore))
    }

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("GetPartitioningCheckpointsIntegrationTests")(
      test("Returns expected sequence of Checkpoints with non-existing partitioning id") {
        for {
          getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
          result <- getPartitioningCheckpoints(GetPartitioningCheckpointsArgs(0L, 10, 0L, None, None, None, None, None))
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      },
      test("Should apply pagination (limit and offset) accurately with combined filters") {
        def getFilteredPage(partitioningId: Long, offset: Long) = getPage(
          GetPartitioningCheckpointsArgs(
            partitioningId = partitioningId,
            limit = 2,
            offset = offset,
            checkpointName = Some(checkpointName),
            checkpointProperties = Some(Map("executionID" -> Seq("a", "b"))),
            latestFirst = None,
            from = None,
            to = None
          )
        )

        for {
          partitioningId <- createPartitioning
          matching1 <- writeCheckpoint(partitioningId, checkpointName, Some("a"), startMinute = 1)
          matching2 <- writeCheckpoint(partitioningId, checkpointName, Some("b"), startMinute = 2)
          _ <- writeCheckpoint(partitioningId, checkpointName, Some("c"), startMinute = 3)
          _ <- writeCheckpoint(partitioningId, "Other checkpoint", Some("a"), startMinute = 4)
          _ <- writeCheckpoint(partitioningId, checkpointName, None, startMinute = 5)
          matching3 <- writeCheckpoint(partitioningId, checkpointName, Some("a"), startMinute = 6)
          firstPage <- getFilteredPage(partitioningId, offset = 0L)
          secondPage <- getFilteredPage(partitioningId, offset = 2L)
        } yield assertTrue(
          firstPage == (Seq(matching3, matching2), true),
          secondPage == (Seq(matching1), false)
        )
      },
      test("Should apply the earliest-first order and the process start time window") {
        def getWindowPage(partitioningId: Long) = getPage(
          GetPartitioningCheckpointsArgs(
            partitioningId = partitioningId,
            limit = 10,
            offset = 0L,
            checkpointName = None,
            checkpointProperties = None,
            latestFirst = Some(false),
            from = Some(firstStartTime.plusMinutes(2)),
            to = Some(firstStartTime.plusMinutes(4))
          )
        )

        for {
          partitioningId <- createPartitioning
          _ <- writeCheckpoint(partitioningId, checkpointName, None, startMinute = 1)
          atWindowStart <- writeCheckpoint(partitioningId, checkpointName, None, startMinute = 2)
          inWindow <- writeCheckpoint(partitioningId, checkpointName, None, startMinute = 3)
          _ <- writeCheckpoint(partitioningId, checkpointName, None, startMinute = 4)
          page <- getWindowPage(partitioningId)
        } yield assertTrue(page == (Seq(atWindowStart, inWindow), false))
      }
    ).provide(
      GetPartitioningCheckpoints.layer,
      CreatePartitioning.layer,
      WriteCheckpointV2.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithSingleTransactionRollback
    )
  }

}

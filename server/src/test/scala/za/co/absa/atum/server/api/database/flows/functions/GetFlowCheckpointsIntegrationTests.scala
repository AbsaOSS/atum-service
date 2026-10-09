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

package za.co.absa.atum.server.api.database.flows.functions

import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints.GetFlowCheckpointsArgs
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpointV2.WriteCheckpointArgs
import za.co.absa.atum.server.api.database.runs.functions.{
  CreatePartitioning,
  GetPartitioningMainFlow,
  WriteCheckpointV2
}
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio._
import zio.test._
import zio.interop.catz.asyncInstance

import java.time.ZonedDateTime
import java.util.UUID

object GetFlowCheckpointsIntegrationTests extends ConfigProviderTest {

  private val checkpointName = "Data written"
  private val firstStartTime = ZonedDateTime.parse("2026-01-01T00:00:00Z")

  private def createPartitioning: ZIO[CreatePartitioning, Throwable, Long] =
    ZIO
      .serviceWithZIO[CreatePartitioning](
        _(PartitioningSubmitV2DTO(Seq(PartitionDTO("key", UUID.randomUUID().toString)), None, "author"))
      )
      .flatMap(ZIO.fromEither(_))
      .map(_.data)

  private def getMainFlowId(partitioningId: Long): ZIO[GetPartitioningMainFlow, Throwable, Long] =
    ZIO
      .serviceWithZIO[GetPartitioningMainFlow](_(partitioningId))
      .flatMap(ZIO.fromEither(_))
      .flatMap(row => ZIO.getOrFail(row.data.map(_.id)))

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
  private def getPage(args: GetFlowCheckpointsArgs): ZIO[GetFlowCheckpoints, Throwable, (Seq[UUID], Boolean)] =
    ZIO.serviceWithZIO[GetFlowCheckpoints](_(args)).flatMap(ZIO.fromEither(_)).map { rows =>
      val items = rows.flatMap(_.data)
      (items.map(_.idCheckpoint), items.exists(_.hasMore))
    }

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("GetFlowCheckpointsIntegrationTests")(
      test("Should return checkpoints with the correct flowId, limit, and offset") {

        val args = GetFlowCheckpoints.GetFlowCheckpointsArgs(
          flowId = 1L,
          limit = 10,
          offset = 0L,
          checkpointName = Some("TestCheckpointName"),
          checkpointProperties = None
        )

        for {
          getFlowCheckpoints <- ZIO.service[GetFlowCheckpoints]
          result <- getFlowCheckpoints(args)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(42, "Flow not found"))))
      },
      test("Should apply pagination (limit and offset) accurately with combined filters") {
        def getFilteredPage(flowId: Long, offset: Long) = getPage(
          GetFlowCheckpointsArgs(
            flowId = flowId,
            limit = 2,
            offset = offset,
            checkpointName = Some(checkpointName),
            checkpointProperties = Some(Map("executionID" -> Seq("a", "b")))
          )
        )

        for {
          partitioningId <- createPartitioning
          flowId <- getMainFlowId(partitioningId)
          matching1 <- writeCheckpoint(partitioningId, checkpointName, Some("a"), startMinute = 1)
          matching2 <- writeCheckpoint(partitioningId, checkpointName, Some("b"), startMinute = 2)
          _ <- writeCheckpoint(partitioningId, checkpointName, Some("c"), startMinute = 3)
          _ <- writeCheckpoint(partitioningId, "Other checkpoint", Some("a"), startMinute = 4)
          _ <- writeCheckpoint(partitioningId, checkpointName, None, startMinute = 5)
          matching3 <- writeCheckpoint(partitioningId, checkpointName, Some("a"), startMinute = 6)
          firstPage <- getFilteredPage(flowId, offset = 0L)
          secondPage <- getFilteredPage(flowId, offset = 2L)
        } yield assertTrue(
          firstPage == (Seq(matching3, matching2), true),
          secondPage == (Seq(matching1), false)
        )
      }
    ).provide(
      GetFlowCheckpoints.layer,
      CreatePartitioning.layer,
      GetPartitioningMainFlow.layer,
      WriteCheckpointV2.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithSingleTransactionRollback
    )
  }

}

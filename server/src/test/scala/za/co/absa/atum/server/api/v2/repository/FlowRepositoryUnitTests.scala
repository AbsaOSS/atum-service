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

package za.co.absa.atum.server.api.v2.repository

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.model.dto.CheckpointWithPartitioningDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints.GetFlowCheckpointsArgs
import za.co.absa.atum.server.api.exception.DatabaseError.NotFoundDatabaseError
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.{FunctionStatus, Row}
import zio._
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.test._

object FlowRepositoryUnitTests extends ZIOSpecDefault with TestData {

  private val getFlowCheckpointsV2Mock = mock(classOf[GetFlowCheckpoints])

  when(getFlowCheckpointsV2Mock.apply(GetFlowCheckpointsArgs(1, Some(1), Some(1), None)))
    .thenReturn(
      ZIO.right(
        Seq(
          Row(FunctionStatus(11, "success"), Some(checkpointItemWithPartitioningFromDB1))
        )
      )
    )
  when(getFlowCheckpointsV2Mock.apply(GetFlowCheckpointsArgs(2, Some(1), Some(1), None)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "success"), None))))
  when(getFlowCheckpointsV2Mock.apply(GetFlowCheckpointsArgs(3, None, None, None)))
    .thenReturn(ZIO.fail(DataNotFoundException(FunctionStatus(42, "Flow not found"))))

  private val getFlowCheckpointsV2MockLayer = ZLayer.succeed(getFlowCheckpointsV2Mock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("FlowRepositoryIntegrationSuite")(
      suite("GetFlowCheckpointsV2Suite")(
        test("Returns expected Right with CheckpointV2DTO") {
          for {
            result <- FlowRepository.getFlowCheckpoints(1, Some(1), Some(1), None)
          } yield assertTrue(result == ResultHasMore(Seq(checkpointWithPartitioningDTO1)))
        },
        test("Returns expected Right with CheckpointV2DTO") {
          for {
            result <- FlowRepository.getFlowCheckpoints(2, Some(1), Some(1), None)
          } yield assertTrue(result == ResultNoMore(Seq.empty[CheckpointWithPartitioningDTO]))
        },
        test("Returns expected DatabaseError") {
          assertZIO(FlowRepository.getFlowCheckpoints(3, None, None, None).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        }
      )
    ).provide(
      FlowRepositoryImpl.layer,
      getFlowCheckpointsV2MockLayer
    )

  }

}

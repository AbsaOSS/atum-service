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

package za.co.absa.atum.server.api.repository

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpointsV2.GetFlowCheckpointsArgs
import za.co.absa.atum.server.api.database.flows.functions.{GetFlowCheckpoints, GetFlowCheckpointsV2}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.atum.server.model.PaginatedResult.ResultNoMore
import zio._
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.test._
import za.co.absa.db.fadb.status.{FunctionStatus, Row}

object FlowRepositoryUnitTests extends ZIOSpecDefault with TestData {

  private val getFlowCheckpointsMock = mock(classOf[GetFlowCheckpoints])

  when(getFlowCheckpointsMock.apply(checkpointQueryDTO1)).thenReturn(ZIO.fail(new Exception("boom!")))
  when(getFlowCheckpointsMock.apply(checkpointQueryDTO2))
    .thenReturn(
      ZIO.right(
        Seq(Row(FunctionStatus(0, "success"), checkpointFromDB1), Row(FunctionStatus(0, "success"), checkpointFromDB2))
      )
    )

  private val getFlowCheckpointsMockLayer = ZLayer.succeed(getFlowCheckpointsMock)

  private val getFlowCheckpointsV2Mock = mock(classOf[GetFlowCheckpointsV2])

  when(getFlowCheckpointsV2Mock.apply(GetFlowCheckpointsArgs(1, Some(1), Some(1), None)))
    .thenReturn(
      ZIO.right(
        Seq(
          Row(FunctionStatus(11, "success"), Some(checkpointItemFromDB1)),
          Row(FunctionStatus(11, "success"), Some(checkpointItemFromDB2))
        )
      )
    )
  when(getFlowCheckpointsV2Mock.apply(GetFlowCheckpointsArgs(2, None, None, None)))
    .thenReturn(ZIO.fail(DataNotFoundException(FunctionStatus(42, "Flow not found"))))

  private val getFlowCheckpointsV2MockLayer = ZLayer.succeed(getFlowCheckpointsV2Mock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("FlowRepositoryIntegrationSuite")(
      suite("GetFlowCheckpointsSuite")(
        test("Returns expected DatabaseError") {
          assertZIO(FlowRepository.getFlowCheckpoints(checkpointQueryDTO1).exit)(
            failsWithA[DatabaseError]
          )
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- FlowRepository.getFlowCheckpoints(checkpointQueryDTO2)
          } yield assertTrue(result == Seq(checkpointFromDB1, checkpointFromDB2))
        }
      ),
      suite("GetFlowCheckpointsV2Suite")(
        test("Returns expected Right with CheckpointV2DTO") {
          for {
            result <- FlowRepository.getFlowCheckpointsV2(1, Some(1), Some(1), None)
          } yield assertTrue(result == ResultNoMore(Seq(checkpointV2DTO1, checkpointV2DTO2)))
        },
        test("Returns expected DatabaseError") {
          assertZIO(FlowRepository.getFlowCheckpointsV2(2, None, None, None).exit)(
            failsWithA[DatabaseError]
          )
        }
      )
    ).provide(
      FlowRepositoryImpl.layer,
      getFlowCheckpointsMockLayer,
      getFlowCheckpointsV2MockLayer
    )

  }

}

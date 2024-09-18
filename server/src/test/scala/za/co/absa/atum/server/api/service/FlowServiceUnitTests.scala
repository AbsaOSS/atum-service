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

package za.co.absa.atum.server.api.service

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.repository.FlowRepository
import za.co.absa.atum.server.model.PaginatedResult.ResultHasMore
import za.co.absa.atum.server.api.exception.DatabaseError.NotFoundDatabaseError
import za.co.absa.atum.server.api.exception.ServiceError.NotFoundServiceError
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object FlowServiceUnitTests extends ZIOSpecDefault with TestData {
  private val flowRepositoryMock = mock(classOf[FlowRepository])

  when(flowRepositoryMock.getFlowCheckpoints(checkpointQueryDTO1)).thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))
  when(flowRepositoryMock.getFlowCheckpoints(checkpointQueryDTO2))
    .thenReturn(ZIO.succeed(Seq(checkpointFromDB2)))

  when(flowRepositoryMock.getFlowCheckpointsV2(1L, None, None, None))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(checkpointV2DTO1))))
  when(flowRepositoryMock.getFlowCheckpointsV2(2L, None, None, None))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("Flow not found")))

  private val flowRepositoryMockLayer = ZLayer.succeed(flowRepositoryMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("FlowServiceSuite")(
      suite("GetFlowCheckpointsSuite")(
        test("Returns expected ServiceError") {
          assertZIO(FlowService.getFlowCheckpoints(checkpointQueryDTO1).exit)(
            failsWithA[ServiceError]
          )
        },
        test("Returns expected Seq[CheckpointDTO]") {
          for {
            result <- FlowService.getFlowCheckpoints(checkpointQueryDTO2)
          } yield assertTrue {
            result == Seq(checkpointDTO2)
          }
        }
      ),
      suite("GetFlowCheckpointsV2Suite")(
        test("Returns expected PaginatedResult[CheckpointV2DTO]") {
          for {
            result <- FlowService.getFlowCheckpointsV2(1L, None, None, None)
          } yield assertTrue {
            result == ResultHasMore(Seq(checkpointV2DTO1))
          }
        },
        test("Returns expected ServiceError") {
          assertZIO(FlowService.getFlowCheckpointsV2(2L, None, None, None).exit)(
            failsWithA[NotFoundServiceError]
          )
        }
      )
    ).provide(
      FlowServiceImpl.layer,
      flowRepositoryMockLayer
    )

  }
}

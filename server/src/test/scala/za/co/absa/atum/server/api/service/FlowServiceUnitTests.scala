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
import za.co.absa.atum.server.api.repository.FlowRepository
import za.co.absa.atum.server.model.PaginatedResult.ResultHasMore
import za.co.absa.atum.server.api.exception.DatabaseError.NotFoundDatabaseError
import za.co.absa.atum.server.api.exception.ServiceError.NotFoundServiceError
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object FlowServiceUnitTests extends ZIOSpecDefault with TestData {
  private val flowRepositoryMock = mock(classOf[FlowRepository])

  when(flowRepositoryMock.getFlowCheckpoints(1L, None, None, None))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(checkpointWithPartitioningDTO1))))
  when(flowRepositoryMock.getFlowCheckpoints(2L, None, None, None))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("Flow not found")))

  private val flowRepositoryMockLayer = ZLayer.succeed(flowRepositoryMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("FlowServiceSuite")(
      suite("GetFlowCheckpointsV2Suite")(
        test("Returns expected PaginatedResult[CheckpointV2DTO]") {
          for {
            result <- FlowService.getFlowCheckpoints(1L, None, None, None)
          } yield assertTrue {
            result == ResultHasMore(Seq(checkpointWithPartitioningDTO1))
          }
        },
        test("Returns expected ServiceError") {
          assertZIO(FlowService.getFlowCheckpoints(2L, None, None, None).exit)(
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

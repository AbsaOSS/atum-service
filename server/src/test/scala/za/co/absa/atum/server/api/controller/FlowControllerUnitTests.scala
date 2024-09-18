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

package za.co.absa.atum.server.api.controller

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.service.FlowService
import za.co.absa.atum.server.model.{InternalServerErrorResponse, NotFoundErrorResponse, Pagination}
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object FlowControllerUnitTests extends ZIOSpecDefault with TestData {
  private val flowServiceMock = mock(classOf[FlowService])

  when(flowServiceMock.getFlowCheckpoints(checkpointQueryDTO1))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(flowServiceMock.getFlowCheckpoints(checkpointQueryDTO2))
    .thenReturn(ZIO.succeed(Seq(checkpointDTO2)))

  when(flowServiceMock.getFlowCheckpointsV2(1L, Some(5), None, None))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(checkpointV2DTO1))))
  when(flowServiceMock.getFlowCheckpointsV2(2L, Some(5), Some(0), None))
    .thenReturn(ZIO.succeed(ResultNoMore(Seq(checkpointV2DTO2))))
  when(flowServiceMock.getFlowCheckpointsV2(3L, Some(5), Some(0), None))
    .thenReturn(ZIO.fail(NotFoundServiceError("Flow not found")))

  private val flowServiceMockLayer = ZLayer.succeed(flowServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("FlowControllerSuite")(
      suite("GetFlowCheckpointsSuite")(
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(FlowController.getFlowCheckpointsV2(checkpointQueryDTO1).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected CheckpointDTO") {
          for {
            result <- FlowController.getFlowCheckpointsV2(checkpointQueryDTO2)
          } yield assertTrue(result.data == Seq(checkpointDTO2))
        }
      ),
      suite("GetFlowCheckpointsV2Suite")(
        test("Returns expected Seq[CheckpointV2DTO] with Pagination indicating there is more data available") {
          for {
            result <- FlowController.getFlowCheckpoints(1L, Some(5), None, None)
          } yield assertTrue(result.data == Seq(checkpointV2DTO1) && result.pagination == Pagination(5, 0, hasMore = true))
        },
        test("Returns expected Seq[CheckpointV2DTO] with Pagination indicating there is no more data available") {
          for {
            result <- FlowController.getFlowCheckpoints(2L, Some(5), Some(0), None)
          } yield assertTrue(result.data == Seq(checkpointV2DTO2) && result.pagination == Pagination(5, 0, hasMore = false))
        },
        test("Returns expected NotFoundServiceError when service returns NotFoundServiceError") {
          assertZIO(FlowController.getFlowCheckpoints(3L, Some(5), Some(0), None).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        }
      )
    ).provide(
      FlowControllerImpl.layer,
      flowServiceMockLayer
    )
  }
}

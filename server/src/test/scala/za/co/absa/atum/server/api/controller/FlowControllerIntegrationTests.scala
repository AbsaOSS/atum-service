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
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.service.FlowService
import za.co.absa.atum.server.model.InternalServerErrorResponse
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object FlowControllerIntegrationTests extends ZIOSpecDefault with TestData {
  private val flowServiceMock = mock(classOf[FlowService])
  when(flowServiceMock.getFlowCheckpoints(checkpointQueryDTO1))
    .thenReturn(ZIO.fail(ServiceError("boom!")))

  when(flowServiceMock.getFlowCheckpoints(checkpointQueryDTO2))
    .thenReturn(ZIO.succeed(Seq(checkpointDTO2)))

  private val flowServiceMockLayer = ZLayer.succeed(flowServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("FlowControllerSuite")(
      suite("GetFlowCheckpointsSuite")(
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(FlowController.getFlowCheckpoints(checkpointQueryDTO1).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },

        test("Returns expected CheckpointDTO") {
          for {
            result <- FlowController.getFlowCheckpoints(checkpointQueryDTO2)
          } yield assertTrue (result == Seq(checkpointDTO2))
        }

      )
    ).provide(
      FlowControllerImpl.layer,
      flowServiceMockLayer
    )
  }
}

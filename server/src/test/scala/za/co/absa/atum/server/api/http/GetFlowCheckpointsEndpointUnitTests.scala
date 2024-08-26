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

package za.co.absa.atum.server.api.http

import org.mockito.Mockito.{mock, when}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, basicRequest}
import sttp.client3.circe._
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.FlowController
import za.co.absa.atum.server.model.{GeneralErrorResponse, InternalServerErrorResponse}
import za.co.absa.atum.server.model.SuccessResponse.MultiSuccessResponse
import zio._
import zio.test.Assertion.equalTo
import zio.test._

object GetFlowCheckpointsEndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val flowControllerMock = mock(classOf[FlowController])

  when(flowControllerMock.getFlowCheckpointsV2(checkpointQueryDTO1))
    .thenReturn(ZIO.succeed(MultiSuccessResponse(Seq(checkpointDTO1, checkpointDTO2), uuid1)))
  when(flowControllerMock.getFlowCheckpointsV2(checkpointQueryDTO2))
    .thenReturn(ZIO.fail(GeneralErrorResponse("error")))
  when(flowControllerMock.getFlowCheckpointsV2(checkpointQueryDTO3))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))

  private val flowControllerMockLayer = ZLayer.succeed(flowControllerMock)

  private val getFlowCheckpointsServerEndpoint =
    getFlowCheckpointsEndpointV2.zServerLogic(FlowController.getFlowCheckpointsV2)

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[FlowController]))
      .whenServerEndpoint(getFlowCheckpointsServerEndpoint)
      .thenRunLogic()
      .backend()

    val request = basicRequest
      .post(uri"https://test.com/api/v2/get-flow-checkpoints")
      .response(asJson[MultiSuccessResponse[CheckpointDTO]])

    suite("GetFlowCheckpointsEndpointSuite")(
      test("Returns expected CheckpointDTO") {
        val response = request
          .body(checkpointQueryDTO1)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        val expectedResult = MultiSuccessResponse(Seq(checkpointDTO1, checkpointDTO2), uuid1)

        assertZIO(body <&> statusCode)(equalTo(Right(expectedResult), StatusCode.Ok))
      },
      test("Returns expected BadRequest") {
        val response = request
          .body(checkpointQueryDTO2)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test("Returns expected InternalServerError") {
        val response = request
          .body(checkpointQueryDTO3)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    )
  }.provide(
    flowControllerMockLayer
  )

}

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
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.FlowDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.PartitioningController
import za.co.absa.atum.server.model.NotFoundErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio._
import zio.test.Assertion.equalTo
import zio.test._

object GetPartitioningMainFlowEndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioningMainFlow(1L))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(flowDTO1, uuid1)))
  when(partitioningControllerMock.getPartitioningMainFlow(2L))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("partitioning not found")))

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getPartitioningMainFlowServerEndpointV2 = getPartitioningMainFlowEndpointV2
    .zServerLogic({ partitioningId: Long =>
      PartitioningController.getPartitioningMainFlow(partitioningId)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningMainFlowServerEndpointV2)
      .thenRunLogic()
      .backend()

    suite("GetPartitioningMainFlowEndpointSuite")(
      test("Returns an expected FlowDTO") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/main-flow")
          .response(asJson[SingleSuccessResponse[FlowDTO]])

        val response = request.send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(Right(SingleSuccessResponse(flowDTO1, uuid1)), StatusCode.Ok)
        )
      },
      test("Returns expected 404 when partitioning not found for a given id") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/2/main-flow")
          .response(asJson[SingleSuccessResponse[FlowDTO]])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      }
    )
  }.provide(partitioningControllerMockLayer)
}
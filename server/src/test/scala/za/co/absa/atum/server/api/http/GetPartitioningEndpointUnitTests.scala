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
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import sttp.client3.circe._
import sttp.model.StatusCode
import za.co.absa.atum.model.dto.PartitioningWithIdDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.PartitioningController
import za.co.absa.atum.server.model.GeneralErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio.test.Assertion.equalTo
import zio.{Scope, ZIO, ZLayer}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}

object GetPartitioningEndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val getPartitioningEndpointMock = mock(classOf[PartitioningController])

  when(getPartitioningEndpointMock.getPartitioningV2(1111L))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(partitioningWithIdDTO1)))
  when(getPartitioningEndpointMock.getPartitioningV2(2222L))
    .thenReturn(ZIO.fail(GeneralErrorResponse("error")))

  private val getPartitioningEndpointMockLayer = ZLayer.succeed(getPartitioningEndpointMock)

  private val getPartitioningServerEndpoint =
    getPartitioningEndpointV2.zServerLogic(PartitioningController.getPartitioningV2)

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningServerEndpoint)
      .thenRunLogic()
      .backend()

    val request = basicRequest
      .post(uri"https://test.com/api/v2/get-partitioning")
      .response(asJson[SingleSuccessResponse[PartitioningWithIdDTO]])

    suite("GetPartitioningEndpointSuite")(
      test("Returns expected PartitioningWithIdDTO") {
        val response = request
          .body(1111L)
          .send(backendStub)

        val body = response.map(_.body).as(SingleSuccessResponse(partitioningWithIdDTO1))
        val statusCode = response.map(_.code)
        assertZIO(body)(equalTo(SingleSuccessResponse(partitioningWithIdDTO1)))
        assertZIO(statusCode)(equalTo(StatusCode.Ok))

      },
      test("Returns expected not found error") {
        val response = request
          .body(2222L)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      }
    )
  }.provide(getPartitioningEndpointMockLayer)
}

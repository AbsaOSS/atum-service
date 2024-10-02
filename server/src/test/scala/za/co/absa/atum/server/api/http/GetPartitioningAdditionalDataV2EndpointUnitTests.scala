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
import za.co.absa.atum.model.dto.AdditionalDataDTO
import za.co.absa.atum.model.envelopes.NotFoundErrorResponse
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.PartitioningController
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import zio._
import zio.test.Assertion.equalTo
import zio.test._

object GetPartitioningAdditionalDataV2EndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioningAdditionalDataV2(1L))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(additionalDataDTO1, uuid1)))
  when(partitioningControllerMock.getPartitioningAdditionalDataV2(2L))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("partitioning not found")))

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getPartitioningAdditionalDataServerEndpointV2 = getPartitioningAdditionalDataEndpointV2
    .zServerLogic({ partitioningId: Long =>
      PartitioningController.getPartitioningAdditionalDataV2(partitioningId)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningAdditionalDataServerEndpointV2)
      .thenRunLogic()
      .backend()

    suite("GetPartitioningAdditionalDataV2EndpointSuite")(
      test("Returns an expected AdditionalDataDTO") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/additional-data")
          .response(asJson[SingleSuccessResponse[AdditionalDataDTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(Right(SingleSuccessResponse(additionalDataDTO1, uuid1)), StatusCode.Ok)
        )
      },
      test("Returns expected 404 when partitioning not found for a given id") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/2/additional-data")
          .response(asJson[NotFoundErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      }
    )
  }.provide(partitioningControllerMockLayer)
}

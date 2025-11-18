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

package za.co.absa.atum.server.api.v2.http

import io.circe
import org.mockito.Mockito.{mock, when}
import sttp.client3.circe._
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Identity, RequestT, ResponseException, UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataItemDTO, AdditionalDataPatchDTO}
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.envelopes.{InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object PatchAdditionalDataEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock: PartitioningController = mock(classOf[PartitioningController])

  when(partitioningControllerMock.patchPartitioningAdditionalData(1L, additionalDataPatchDTO1))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(additionalDataDTO1.data, uuid1)))
  when(partitioningControllerMock.patchPartitioningAdditionalData(0L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("error")))
  when(partitioningControllerMock.patchPartitioningAdditionalData(2L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val patchAdditionalDataEndpointLogic = Endpoints.patchPartitioningAdditionalDataEndpoint
    .zServerLogic({ case (partitioningId: Long, additionalDataPatchDTO: AdditionalDataPatchDTO) =>
      PartitioningController.patchPartitioningAdditionalData(partitioningId, additionalDataPatchDTO)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(patchAdditionalDataEndpointLogic)
      .thenRunLogic()
      .backend()

    suite("PatchAdditionalDataEndpointUnitTests")(
      test("Returns expected AdditionalDataDTO") {
        val response = patchRequestForId(1L)
          .body(additionalDataPatchDTO1)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(Right(SingleSuccessResponse(additionalDataDTO1.data, uuid1)), StatusCode.Ok)
        )
      },
      test("Returns NotFoundErrorResponse") {
        val response = patchRequestForId(0L)
          .body(additionalDataPatchDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns InternalServerErrorResponse") {
        val response = patchRequestForId(2L)
          .body(additionalDataPatchDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    )

  }.provide(partitioningControllerMockLayer)

  private def patchRequestForId(id: Long): RequestT[Identity, Either[
    ResponseException[String, circe.Error],
    SingleSuccessResponse[AdditionalDataDTO.Data]
  ], Any] = {
    basicRequest
      .patch(uri"https://test.com/api/v2/partitionings/$id/additional-data")
      .response(asJson[SingleSuccessResponse[AdditionalDataDTO.Data]])
  }

}

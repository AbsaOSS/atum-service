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
import sttp.client3.circe.asJson
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.{FlowDTO, ParentPatchV2DTO}
import za.co.absa.atum.model.envelopes.{InternalServerErrorResponse, NotFoundErrorResponse, Pagination}
import za.co.absa.atum.model.envelopes.SuccessResponse.{PaginatedResponse, SingleSuccessResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.PartitioningController
import zio.test.Assertion.equalTo
import sttp.client3.{UriContext, basicRequest}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object PatchPartitioningParentV2EndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.patchPartitioningParentV2(111L, 1111L, "Jack"))
    .thenReturn(
      ZIO.succeed(
        SingleSuccessResponse(parentPatchV2DTO01, uuid1)
      )
    )
  when(partitioningControllerMock.patchPartitioningParentV2(222L, 2222L, "Jack"))
    .thenReturn(
      ZIO.fail(
        NotFoundErrorResponse("Child Partitioning not found")
      )
    )
  when(partitioningControllerMock.patchPartitioningParentV2(9999L, 9999L, "Bean"))
    .thenReturn(
      ZIO.fail(
        InternalServerErrorResponse("internal server error")
      )
    )

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val patchPartitioningParentServerEndpoint =
    patchPartitioningParentEndpointV2.zServerLogic({ case (partitioningId: Long, parentPartitioningId: Long, byUser: String) =>
      PartitioningController.patchPartitioningParentV2(partitioningId, parentPartitioningId: Long, byUser: String)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(patchPartitioningParentServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("PatchPartitioningParentSuite")(
      test("Returns an expected ParentPatchV2DTO") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/111/parents/1111/parents?byUser=Jack")
          .response(asJson[SingleSuccessResponse[ParentPatchV2DTO]])

        val response = request.send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(Right(SingleSuccessResponse(parentPatchV2DTO01, uuid1)), StatusCode.Ok)
        )
      },
      test("Returns expected 404 when partitioning not found for a given id") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/222/partitionings?parentPartitioningId=1111L&byUser=Jack")
          .response(asJson[SingleSuccessResponse[ParentPatchV2DTO]])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      }
    )
  }.provide(partitioningControllerMockLayer)
}

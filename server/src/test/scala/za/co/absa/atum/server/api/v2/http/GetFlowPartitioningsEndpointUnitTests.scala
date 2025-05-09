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

import org.mockito.Mockito.{mock, when}
import sttp.client3.circe.asJson
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.PartitioningWithIdDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.model.envelopes.{InternalServerErrorResponse, NotFoundErrorResponse, Pagination}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object GetFlowPartitioningsEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getFlowPartitionings(1L, Some(1), Some(0)))
    .thenReturn(
      ZIO.succeed(
        PaginatedResponse(Seq.empty, Pagination(1, 0, hasMore = true), uuid1)
      )
    )
  when(partitioningControllerMock.getFlowPartitionings(2L, Some(1), Some(0)))
    .thenReturn(
      ZIO.fail(
        NotFoundErrorResponse("flow not found")
      )
    )
  when(partitioningControllerMock.getFlowPartitionings(3L, None, None))
    .thenReturn(
      ZIO.fail(
        InternalServerErrorResponse("internal server error")
      )
    )

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getFlowPartitioningsServerEndpoint =
    Endpoints.getFlowPartitioningsEndpoint.zServerLogic({ case (flowId: Long, limit: Option[Int], offset: Option[Long]) =>
      PartitioningController.getFlowPartitionings(flowId, limit, offset)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getFlowPartitioningsServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("GetFlowPartitioningsEndpointSuite")(
      test("Returns an expected PaginatedResponse") {
        val request = basicRequest
          .get(uri"http://localhost:8080/api/v2/flows/1/partitionings?limit=1&offset=0")
          .response(asJson[PaginatedResponse[PartitioningWithIdDTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(PaginatedResponse(Seq.empty[PartitioningWithIdDTO], Pagination(1, 0, hasMore = true), uuid1)),
            StatusCode.Ok
          )
        )
      },
      test("Returns a NotFoundErrorResponse") {
        val request = basicRequest
          .get(uri"http://localhost:8080/api/v2/flows/2/partitionings?limit=1&offset=0")
          .response(asJson[NotFoundErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns an InternalServerErrorResponse") {
        val request = basicRequest
          .get(uri"http://localhost:8080/api/v2/flows/3/partitionings")
          .response(asJson[InternalServerErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    )

  }.provide(partitioningControllerMockLayer)

}

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
import za.co.absa.atum.model.dto.LineageGraphDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.envelopes.{GeneralErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import za.co.absa.atum.server.api.v2.http.Endpoints.getPartitioningLineageGraphEndpoint
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object GetPartitioningLineageGraphEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioningLineageGraph(2L, Some("both"), Some(5)))
    .thenReturn(
      ZIO.succeed(
        SingleSuccessResponse(lineageGraphDTO1, uuid1)
      )
    )
  when(partitioningControllerMock.getPartitioningLineageGraph(8888L, None, None))
    .thenReturn(
      ZIO.fail(
        NotFoundErrorResponse("Partitioning not found")
      )
    )
  when(partitioningControllerMock.getPartitioningLineageGraph(9999L, None, None))
    .thenReturn(
      ZIO.fail(
        InternalServerErrorResponse("internal server error")
      )
    )
  when(partitioningControllerMock.getPartitioningLineageGraph(6666L, None, None))
    .thenReturn(
      ZIO.fail(
        GeneralErrorResponse("error")
      )
    )

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getPartitioningLineageGraphServerEndpoint =
    getPartitioningLineageGraphEndpoint.zServerLogic({
      case (partitioningId: Long, direction: Option[String], depth: Option[Int]) =>
        PartitioningController.getPartitioningLineageGraph(partitioningId, direction, depth)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningLineageGraphServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("GetPartitioningLineageGraphEndpointSuite")(
      test("Returns 200 with LineageGraphDTO in SingleSuccessResponse") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/2/lineage?direction=both&depth=5")
          .response(asJson[SingleSuccessResponse[LineageGraphDTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(SingleSuccessResponse(lineageGraphDTO1, uuid1)),
            StatusCode.Ok
          )
        )
      },
      test("Returns 404 NotFound when partitioning does not exist") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/8888/lineage")
          .response(asJson[NotFoundErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns 500 InternalServerError on unexpected error") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/9999/lineage")
          .response(asJson[InternalServerErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      },
      test("Returns 400 BadRequest for a general error") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/6666/lineage")
          .response(asJson[GeneralErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      }
    )

  }.provide(partitioningControllerMockLayer)

}

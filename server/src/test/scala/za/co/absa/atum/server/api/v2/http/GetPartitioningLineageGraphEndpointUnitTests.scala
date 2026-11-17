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
import sttp.client3.circe.{asJson, asJsonAlways}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.LineageGraphDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.envelopes.{GeneralErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.{PartitioningController, PartitioningControllerImpl}
import za.co.absa.atum.server.api.v2.http.Endpoints.getPartitioningLineageGraphEndpoint
import za.co.absa.atum.server.api.v2.service.PartitioningService
import zio.test.Assertion.{equalTo, isRight}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assert, assertTrue, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object GetPartitioningLineageGraphEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioningLineageGraph(2L, "both", 5))
    .thenReturn(
      ZIO.succeed(
        SingleSuccessResponse(lineageGraphDTO1, uuid1)
      )
    )
  when(partitioningControllerMock.getPartitioningLineageGraph(2L, "upstream", 5))
    .thenReturn(
      ZIO.succeed(
        SingleSuccessResponse(lineageGraphDTO1.copy(direction = "upstream"), uuid1)
      )
    )
  when(partitioningControllerMock.getPartitioningLineageGraph(8888L, "both", 5))
    .thenReturn(
      ZIO.fail(
        NotFoundErrorResponse("Partitioning not found")
      )
    )
  when(partitioningControllerMock.getPartitioningLineageGraph(9999L, "both", 5))
    .thenReturn(
      ZIO.fail(
        InternalServerErrorResponse("internal server error")
      )
    )
  when(partitioningControllerMock.getPartitioningLineageGraph(6666L, "both", 5))
    .thenReturn(
      ZIO.fail(
        GeneralErrorResponse("error")
      )
    )

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val partitioningServiceMock = mock(classOf[PartitioningService])
  when(partitioningServiceMock.getPartitioningLineageGraph(2L, "both", 5))
    .thenReturn(ZIO.succeed(lineageGraphDTO1))
  when(partitioningServiceMock.getPartitioningLineageGraph(2L, "upstream", 5))
    .thenReturn(ZIO.succeed(lineageGraphDTO1.copy(direction = "upstream")))

  private val realControllerLayer =
    ZLayer.succeed(partitioningServiceMock) >>> PartitioningControllerImpl.layer

  private val getPartitioningLineageGraphServerEndpoint =
    getPartitioningLineageGraphEndpoint.zServerLogic((PartitioningController.getPartitioningLineageGraph _).tupled)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningLineageGraphServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("GetPartitioningLineageGraphEndpointAllSuite")(
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
              (
                Right(SingleSuccessResponse(lineageGraphDTO1, uuid1)),
                StatusCode.Ok
              )
            )
          )
        },
        test("Returns 200 OK with omitted defaults (no direction or depth query params)") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/2/lineage")
            .response(asJson[SingleSuccessResponse[LineageGraphDTO]])

          val response = request.send(backendStub)

          assertZIO(response.map(r => (r.body, r.code)))(
            equalTo((Right(SingleSuccessResponse(lineageGraphDTO1, uuid1)), StatusCode.Ok))
          )
        },
        test("Normalizes trimmed/uppercase direction query param (%20%20UPSTREAM%20) and returns 200 OK") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/2/lineage?direction=%20%20UPSTREAM%20")
            .response(asJson[SingleSuccessResponse[LineageGraphDTO]])

          val response = request.send(backendStub)

          assertZIO(response.map(r => (r.body, r.code)))(
            equalTo((Right(SingleSuccessResponse(lineageGraphDTO1.copy(direction = "upstream"), uuid1)), StatusCode.Ok))
          )
        },
        test("Returns 404 NotFound with decoded NotFoundErrorResponse when partitioning does not exist") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/8888/lineage")
            .response(asJsonAlways[NotFoundErrorResponse])

          for {
            response <- request.send(backendStub)
          } yield assertTrue(
            response.code == StatusCode.NotFound,
            response.body.map(_.message) == Right("Partitioning not found")
          )
        },
        test("Returns 500 InternalServerError with decoded InternalServerErrorResponse on unexpected error") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/9999/lineage")
            .response(asJsonAlways[InternalServerErrorResponse])

          for {
            response <- request.send(backendStub)
          } yield assertTrue(
            response.code == StatusCode.InternalServerError,
            response.body.map(_.message) == Right("internal server error")
          )
        },
        test("Returns 400 BadRequest with decoded GeneralErrorResponse for a general error") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/6666/lineage")
            .response(asJsonAlways[GeneralErrorResponse])

          for {
            response <- request.send(backendStub)
          } yield assertTrue(
            response.code == StatusCode.BadRequest,
            response.body.map(_.message) == Right("error")
          )
        }
      ).provide(partitioningControllerMockLayer),
      suite("GetPartitioningLineageGraphRealControllerValidationSuite")(
        test("Returns 200 OK without direction/depth query params using real controller defaults") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/2/lineage")
            .response(asJson[SingleSuccessResponse[LineageGraphDTO]])

          for {
            response <- request.send(backendStub)
          } yield assertTrue(response.code == StatusCode.Ok) &&
            assert(response.body.map(_.data))(isRight(equalTo(lineageGraphDTO1)))
        },
        test("Returns 200 OK for trimmed/uppercase direction via real controller") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/2/lineage?direction=%20%20UPSTREAM%20")
            .response(asJson[SingleSuccessResponse[LineageGraphDTO]])

          for {
            response <- request.send(backendStub)
          } yield assertTrue(response.code == StatusCode.Ok) &&
            assert(response.body.map(_.data))(isRight(equalTo(lineageGraphDTO1.copy(direction = "upstream"))))
        },
        test("Returns 400 BadRequest for invalid direction") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/2/lineage?direction=sideways")

          for {
            response <- request.send(backendStub)
          } yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("Returns 400 BadRequest for depth=0 (below range)") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/2/lineage?depth=0")

          for {
            response <- request.send(backendStub)
          } yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("Returns 400 BadRequest for depth=21 (above range)") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/2/lineage?depth=21")

          for {
            response <- request.send(backendStub)
          } yield assertTrue(response.code == StatusCode.BadRequest)
        },
        test("Returns 400 BadRequest for non-numeric partitioningId") {
          val request = basicRequest
            .get(uri"https://test.com/api/v2/partitionings/not-a-number/lineage")

          for {
            response <- request.send(backendStub)
          } yield assertTrue(response.code == StatusCode.BadRequest)
        }
      ).provide(realControllerLayer)
    )
  }

}

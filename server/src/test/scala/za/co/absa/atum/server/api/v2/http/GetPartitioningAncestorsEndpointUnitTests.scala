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
import za.co.absa.atum.model.dto.PartitioningWithIdDTO
import za.co.absa.atum.model.envelopes.{GeneralErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse, Pagination}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import za.co.absa.atum.server.api.v2.http.Endpoints.getPartitioningAncestorsEndpoint
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object GetPartitioningAncestorsEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioningAncestors(1111L, 1, 0L))
    .thenReturn(
      ZIO.succeed(
        PaginatedResponse(Seq(partitioningWithIdDTO1), Pagination(1, 0, hasMore = true), uuid1)
      )
    )
  when(partitioningControllerMock.getPartitioningAncestors(7777L, 10, 0L))
    .thenReturn(
      ZIO.succeed(
        PaginatedResponse(
          Seq(partitioningWithIdDTO1, partitioningWithIdDTO2),
          Pagination(10, 0, hasMore = false),
          uuid1
        )
      )
    )
  when(partitioningControllerMock.getPartitioningAncestors(6666L, 1, 0L))
    .thenReturn(
      ZIO.fail(
        GeneralErrorResponse("bad request")
      )
    )
  when(partitioningControllerMock.getPartitioningAncestors(8888L, 1, 0L))
    .thenReturn(
      ZIO.fail(
        NotFoundErrorResponse("Partitioning not found")
      )
    )
  when(partitioningControllerMock.getPartitioningAncestors(9999L, 1, 0L))
    .thenReturn(
      ZIO.fail(
        InternalServerErrorResponse("internal server error")
      )
    )

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getPartitioningAncestorsServerEndpoint =
    getPartitioningAncestorsEndpoint.zServerLogic((PartitioningController.getPartitioningAncestors _).tupled)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningAncestorsServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("GetAncestorsEndpointSuite")(
      test("Returns an expected PaginatedResponse") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1111/ancestors?limit=1&offset=0")
          .response(asJson[PaginatedResponse[PartitioningWithIdDTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            (
              Right(PaginatedResponse(Seq(partitioningWithIdDTO1), Pagination(1, 0, hasMore = true), uuid1)),
              StatusCode.Ok
            )
          )
        )
      },
      test("Returns all ancestors of a multi-parent child in the standard envelope") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/7777/ancestors?limit=10&offset=0")
          .response(asJson[PaginatedResponse[PartitioningWithIdDTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            (
              Right(
                PaginatedResponse(
                  Seq(partitioningWithIdDTO1, partitioningWithIdDTO2),
                  Pagination(10, 0, hasMore = false),
                  uuid1
                )
              ),
              StatusCode.Ok
            )
          )
        )
      },
      test("Omitted limit and offset query params default to limit=10, offset=0") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/7777/ancestors")
          .response(asJson[PaginatedResponse[PartitioningWithIdDTO]])

        for {
          response <- request.send(backendStub)
        } yield assertTrue(
          response.code == StatusCode.Ok,
          response.body == Right(
            PaginatedResponse(
              Seq(partitioningWithIdDTO1, partitioningWithIdDTO2),
              Pagination(10, 0, hasMore = false),
              uuid1
            )
          )
        )
      },
      test("Returns 400 BadRequest when limit=0") {
        for {
          response <- basicRequest.get(uri"https://test.com/api/v2/partitionings/7777/ancestors?limit=0").send(backendStub)
        } yield assertTrue(response.code == StatusCode.BadRequest)
      },
      test("Returns 400 BadRequest when limit=1001") {
        for {
          response <- basicRequest.get(uri"https://test.com/api/v2/partitionings/7777/ancestors?limit=1001").send(backendStub)
        } yield assertTrue(response.code == StatusCode.BadRequest)
      },
      test("Returns 400 BadRequest when offset=-1") {
        for {
          response <- basicRequest.get(uri"https://test.com/api/v2/partitionings/7777/ancestors?offset=-1").send(backendStub)
        } yield assertTrue(response.code == StatusCode.BadRequest)
      },
      test("Returns 400 BadRequest when partitioningId is non-numeric") {
        for {
          response <- basicRequest.get(uri"https://test.com/api/v2/partitionings/abc/ancestors").send(backendStub)
        } yield assertTrue(response.code == StatusCode.BadRequest)
      },
      test("Returns a GeneralErrorResponse") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/6666/ancestors?limit=1&offset=0")
          .response(asJsonAlways[GeneralErrorResponse])

        for {
          response <- request.send(backendStub)
        } yield assertTrue(
          response.code == StatusCode.BadRequest,
          response.body.map(_.message) == Right("bad request")
        )
      },
      test("Returns a NotFoundErrorResponse") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/8888/ancestors?limit=1&offset=0")
          .response(asJsonAlways[NotFoundErrorResponse])

        for {
          response <- request.send(backendStub)
        } yield assertTrue(
          response.code == StatusCode.NotFound,
          response.body.map(_.message) == Right("Partitioning not found")
        )
      },
      test("Returns an InternalServerErrorResponse") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/9999/ancestors?limit=1&offset=0")
          .response(asJsonAlways[InternalServerErrorResponse])

        for {
          response <- request.send(backendStub)
        } yield assertTrue(
          response.code == StatusCode.InternalServerError,
          response.body.map(_.message) == Right("internal server error")
        )
      }
    )

  }.provide(partitioningControllerMockLayer)

}

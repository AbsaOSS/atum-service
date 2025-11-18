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
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.model.envelopes.{NotFoundErrorResponse, Pagination}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.CheckpointController
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

import java.util.UUID

object GetPartitioningCheckpointsEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val checkpointControllerMock = mock(classOf[CheckpointController])

  private val uuid = UUID.randomUUID()

  when(checkpointControllerMock.getPartitioningCheckpoints(1L, 10, 0L, None, includeProperties = false))
    .thenReturn(ZIO.succeed(PaginatedResponse(Seq(checkpointV2DTO1), Pagination(10, 0, hasMore = true), uuid)))
  when(checkpointControllerMock.getPartitioningCheckpoints(1L, 10, 0L, None, includeProperties = true))
    .thenReturn(
      ZIO.succeed(
        PaginatedResponse(
          Seq(checkpointV2DTO1.copy(properties = Some(Map("propName1" -> "propValue1")))),
          Pagination(10, 0, hasMore = true),
          uuid
        )
      )
    )
  when(checkpointControllerMock.getPartitioningCheckpoints(1L, 20, 0L, None, includeProperties = false))
    .thenReturn(ZIO.succeed(PaginatedResponse(Seq(checkpointV2DTO1), Pagination(20, 0, hasMore = false), uuid)))
  when(checkpointControllerMock.getPartitioningCheckpoints(2L, 10, 0L, None, includeProperties = false))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("partitioning not found")))
  when(checkpointControllerMock.getPartitioningCheckpoints(3L, 10, 0L, None, includeProperties = false))
    .thenReturn(ZIO.succeed(PaginatedResponse(Seq(checkpointV2DTO1), Pagination(10, 0, hasMore = true), uuid)))

  private val checkpointControllerMockLayer = ZLayer.succeed(checkpointControllerMock)

  private val getPartitioningCheckpointServerEndpointV2 = Endpoints.getPartitioningCheckpointsEndpoint
    .zServerLogic({
      case (
            partitioningId: Long,
            limit: Int,
            offset: Long,
            checkpointName: Option[String],
            includeProperties: Boolean
          ) =>
        CheckpointController.getPartitioningCheckpoints(
          partitioningId,
          limit,
          offset,
          checkpointName,
          includeProperties
        )
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[CheckpointController]))
      .whenServerEndpoint(getPartitioningCheckpointServerEndpointV2)
      .thenRunLogic()
      .backend()

    suite("GetPartitioningCheckpointsEndpointSuite")(
      test("Returns an expected PaginatedResponse[CheckpointV2DTO] with more data available") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/checkpoints?limit=10&offset=0")
          .response(asJson[PaginatedResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(PaginatedResponse(Seq(checkpointV2DTO1), Pagination(10, 0, hasMore = true), uuid)),
            StatusCode.Ok
          )
        )
      },
      test("Returns an expected PaginatedResponse[CheckpointV2DTO] with more data available with properties") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/checkpoints?limit=10&offset=0&include-properties=true")
          .response(asJson[PaginatedResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(
              PaginatedResponse(
                Seq(checkpointV2DTO1.copy(properties = Some(Map("propName1" -> "propValue1")))),
                Pagination(10, 0, hasMore = true),
                uuid
              )
            ),
            StatusCode.Ok
          )
        )
      },
      test("Returns an expected PaginatedResponse[CheckpointV2DTO] with no more data available") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/checkpoints?limit=20&offset=0")
          .response(asJson[PaginatedResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(PaginatedResponse(Seq(checkpointV2DTO1), Pagination(20, 0, hasMore = false), uuid)),
            StatusCode.Ok
          )
        )
      },
      test("Returns expected 404 when checkpoint data for a given ID doesn't exist") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/2/checkpoints?limit=10&offset=0")
          .response(asJson[PaginatedResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns expected 400 when limit is out of range") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/checkpoints?limit=1001&offset=0")
          .response(asJson[PaginatedResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test("Returns expected 400 when offset is negative") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/checkpoints?limit=10&offset=-1")
          .response(asJson[PaginatedResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test(
        "Returns an expected PaginatedResponse[CheckpointV2DTO] with more data available " +
          "(call without optional params)"
      ) {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/3/checkpoints")
          .response(asJson[PaginatedResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(PaginatedResponse(Seq(checkpointV2DTO1), Pagination(10, 0, hasMore = true), uuid)),
            StatusCode.Ok
          )
        )
      }
    )
  }.provide(
    checkpointControllerMockLayer
  )

}

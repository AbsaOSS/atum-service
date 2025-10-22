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
import za.co.absa.atum.model.dto.CheckpointWithPartitioningDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.model.envelopes.{NotFoundErrorResponse, Pagination}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.FlowController
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

import java.util.UUID

object GetFlowCheckpointsEndpointUnitTests extends ZIOSpecDefault with TestData {
  private val flowControllerMockV2 = mock(classOf[FlowController])
  private val uuid = UUID.randomUUID()

  when(flowControllerMockV2.getFlowCheckpoints(1L, 5, 0L, None))
    .thenReturn(
      ZIO.succeed(PaginatedResponse(Seq(checkpointWithPartitioningDTO1), Pagination(5, 0, hasMore = true), uuid))
    )
  when(flowControllerMockV2.getFlowCheckpoints(2L, 5, 0L, None))
    .thenReturn(
      ZIO.succeed(PaginatedResponse(Seq(checkpointWithPartitioningDTO2), Pagination(5, 0, hasMore = false), uuid))
    )
  when(flowControllerMockV2.getFlowCheckpoints(3L, 5, 0L, None))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("Flow not found for a given ID")))

  private val flowControllerMockLayerV2 = ZLayer.succeed(flowControllerMockV2)

  private val getFlowCheckpointServerEndpoint = Endpoints.getFlowCheckpointsEndpoint.zServerLogic({
    case (flowId: Long, limit: Int, offset: Long, checkpointName: Option[String]) =>
      FlowController.getFlowCheckpoints(flowId, limit, offset, checkpointName)
  })

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[FlowController]))
      .whenServerEndpoint(getFlowCheckpointServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("GetFlowCheckpointsEndpointSuite")(
      test("Returns an expected PaginatedResponse[CheckpointV2DTO] with more data available") {
        val baseUri = uri"https://test.com/api/v2/flows/1/checkpoints?limit=5&offset=0"
        val response = basicRequest
          .get(baseUri)
          .response(asJson[PaginatedResponse[CheckpointWithPartitioningDTO]])
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(PaginatedResponse(Seq(checkpointWithPartitioningDTO1), Pagination(5, 0, hasMore = true), uuid)),
            StatusCode.Ok
          )
        )
      },
      test("Returns an expected PaginatedResponse[CheckpointV2DTO] with no more data available") {
        val baseUri = uri"https://test.com/api/v2/flows/2/checkpoints?limit=5&offset=0"
        val response = basicRequest
          .get(baseUri)
          .response(asJson[PaginatedResponse[CheckpointWithPartitioningDTO]])
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        println(s"body: $body and statusCode: $statusCode")

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(PaginatedResponse(Seq(checkpointWithPartitioningDTO2), Pagination(5, 0, hasMore = false), uuid)),
            StatusCode.Ok
          )
        )
      },
      test("Returns expected 404 when checkpoint data for a given ID doesn't exist") {
        val baseUri = uri"https://test.com/api/v2/flows/3/checkpoints?limit=5&offset=0"
        val response = basicRequest
          .get(baseUri)
          .response(asJson[PaginatedResponse[CheckpointWithPartitioningDTO]])
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns expected 400 when limit is out of range") {
        val baseUri = uri"https://test.com/api/v2/flows/1/checkpoints?limit=1005&offset=0"
        val response = basicRequest
          .get(baseUri)
          .response(asJson[PaginatedResponse[CheckpointWithPartitioningDTO]])
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test("Returns expected 400 when offset is negative") {
        val baseUri = uri"https://test.com/api/v2/flows/1/checkpoints?limit=-1&offset=0"
        val response = basicRequest
          .get(baseUri)
          .response(asJson[PaginatedResponse[CheckpointWithPartitioningDTO]])
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      }
    )
  }.provide(
    flowControllerMockLayerV2
  )
}

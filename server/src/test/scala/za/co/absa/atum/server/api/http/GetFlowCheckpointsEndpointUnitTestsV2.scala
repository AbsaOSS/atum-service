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
import sttp.client3.{Identity, RequestT, ResponseException, UriContext, basicRequest}
import sttp.client3.circe.asJson
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.FlowController
import za.co.absa.atum.server.model.{NotFoundErrorResponse, Pagination}
import za.co.absa.atum.server.model.SuccessResponse.PaginatedResponse
import zio.{Scope, ZIO, ZLayer}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.test.Assertion.equalTo

import java.util.UUID

object GetFlowCheckpointsEndpointUnitTestsV2 extends ZIOSpecDefault with Endpoints with TestData {
  private val flowControllerMockV2 = mock(classOf[FlowController])
  private val uuid = UUID.randomUUID()

  when(flowControllerMockV2.getFlowCheckpoints(1L, Some(5), Some(0), None))
    .thenReturn(ZIO.succeed(PaginatedResponse(Seq(checkpointV2DTO1), Pagination(5, 0, hasMore = true), uuid)))
  when(flowControllerMockV2.getFlowCheckpoints(2L, Some(5), Some(0), None))
    .thenReturn(ZIO.succeed(PaginatedResponse(Seq(checkpointV2DTO2), Pagination(5, 0, hasMore = false), uuid)))
  when(flowControllerMockV2.getFlowCheckpoints(3L, Some(5), Some(0), None))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("Flow not found for a given ID")))

  private val flowControllerMockLayerV2 = ZLayer.succeed(flowControllerMockV2)

  private val getFlowCheckpointServerEndpointV2 = getFlowCheckpointsEndpoint.zServerLogic({
      case (flowId: Long, limit: Option[Int], offset: Option[Long], checkpointName: Option[String]) =>
        FlowController.getFlowCheckpoints(flowId, limit, offset, checkpointName)
    })

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[FlowController]))
      .whenServerEndpoint(getFlowCheckpointServerEndpointV2)
      .thenRunLogic()
      .backend()

    def createBasicRequest(flowId: Long, limit: Option[Int], offset: Option[Long], checkpointName: Option[String]
                          ): RequestT[Identity, Either[ResponseException[String, io.circe.Error], PaginatedResponse[CheckpointV2DTO]], Any] = {
      basicRequest
        .get(uri"https://test.com/api/v2/partitionings/$flowId/flows"
        .addParam("limit", limit.map(_.toString).getOrElse("10"))
        .addParam("offset", offset.map(_.toString).getOrElse("0"))
        .addParam("checkpointName", checkpointName.getOrElse("")))
        .response(asJson[PaginatedResponse[CheckpointV2DTO]])
    }

    suite("GetFlowCheckpointsEndpointSuite")(
      test("Returns an expected PaginatedResponse[CheckpointV2DTO] with more data available") {

        val response = createBasicRequest(1L, Some(5), Some(0), None)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(PaginatedResponse(Seq(checkpointV2DTO1), Pagination(5, 0, hasMore = true), uuid)),
            StatusCode.Ok
          )
        )
      },
      test("Returns an expected PaginatedResponse[CheckpointV2DTO] with no more data available") {
        val response = createBasicRequest(2L, Some(5), Some(0), None)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        println(s"body: $body and statusCode: $statusCode")

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(PaginatedResponse(Seq(checkpointV2DTO1), Pagination(5, 0, hasMore = true), uuid)),
            StatusCode.Ok
          )
        )
      },
      test("Returns expected 404 when checkpoint data for a given ID doesn't exist") {
        val response = createBasicRequest(3L, Some(5), Some(0), None)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns expected 400 when limit is out of range") {
        val response = createBasicRequest(1L, Some(10000), Some(0), None)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test("Returns expected 400 when offset is negative") {
        val response = createBasicRequest(1L, Some(10), Some(-1), None)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      }
    )

  }.provide(
    flowControllerMockLayerV2
  )
}

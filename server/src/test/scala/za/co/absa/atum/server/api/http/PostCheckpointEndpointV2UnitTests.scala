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
import sttp.client3.{UriContext, basicRequest}
import sttp.client3.circe._
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.model.envelopes.{ConflictErrorResponse, GeneralErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.CheckpointController
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import zio._
import zio.test.Assertion.equalTo
import zio.test._

object PostCheckpointEndpointV2UnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val checkpointControllerMock = mock(classOf[CheckpointController])

  when(checkpointControllerMock.postCheckpointV2(1L, checkpointV2DTO1))
    .thenReturn(ZIO.succeed((SingleSuccessResponse(checkpointV2DTO1, uuid1), "some location")))
  when(checkpointControllerMock.postCheckpointV2(1L, checkpointV2DTO2))
    .thenReturn(ZIO.fail(GeneralErrorResponse("error")))
  when(checkpointControllerMock.postCheckpointV2(1L, checkpointV2DTO3))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))
  when(checkpointControllerMock.postCheckpointV2(1L, checkpointV2DTO4))
    .thenReturn(ZIO.fail(ConflictErrorResponse("error")))
  when(checkpointControllerMock.postCheckpointV2(0L, checkpointV2DTO4))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("Partitioning not found")))

  private val checkpointControllerMockLayer = ZLayer.succeed(checkpointControllerMock)

  private val postCheckpointServerEndpointV2 = postCheckpointEndpointV2
    .zServerLogic({ case (partitioningId: Long, checkpointV2DTO: CheckpointV2DTO) =>
      CheckpointController.postCheckpointV2(partitioningId, checkpointV2DTO)
    })

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[CheckpointController]))
      .whenServerEndpoint(postCheckpointServerEndpointV2)
      .thenRunLogic()
      .backend()

    val request = basicRequest
      .post(uri"https://test.com/api/v2/partitionings/1/checkpoints")
      .response(asJson[SingleSuccessResponse[CheckpointV2DTO]])

    suite("CreateCheckpointEndpointSuite")(
      test("Returns expected CheckpointDTO") {
        val response = request
          .body(checkpointV2DTO1)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)
        val header = response.map(_.header("Location"))

        assertZIO(body <&> statusCode <&> header)(
          equalTo(Right(SingleSuccessResponse(checkpointV2DTO1, uuid1)), StatusCode.Created, Some("some location"))
        )
      },
      test("Returns expected BadRequest") {
        val response = request
          .body(checkpointV2DTO2)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test("Returns expected InternalServerError") {
        val response = request
          .body(checkpointV2DTO3)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      },
      test("Returns expected ConflictError") {
        val response = request
          .body(checkpointV2DTO4)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.Conflict))
      },
      test("Returns expected NotFound") {
        val response = request
          .post(uri"https://test.com/api/v2/partitionings/0/checkpoints")
          .body(checkpointV2DTO4)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      }
    )
  }.provide(
    checkpointControllerMockLayer
  )
}

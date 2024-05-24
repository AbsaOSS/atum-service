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
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.client3.playJson._
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.CheckpointController
import za.co.absa.atum.server.model.{GeneralErrorResponse, InternalServerErrorResponse}
import zio.test._
import zio._
import za.co.absa.atum.server.model.PlayJsonImplicits.{readsCheckpointDTO, writesCheckpointDTO}
import zio.test.Assertion.equalTo

object CreateCheckpointEndpointIntegrationTests extends ZIOSpecDefault with Endpoints with TestData {

  private val checkpointControllerMock = mock(classOf[CheckpointController])

  when(checkpointControllerMock.createCheckpoint(checkpointDTO1))
    .thenReturn(ZIO.succeed(checkpointDTO1))
  when(checkpointControllerMock.createCheckpoint(checkpointDTO2))
    .thenReturn(ZIO.fail(GeneralErrorResponse("error")))
  when(checkpointControllerMock.createCheckpoint(checkpointDTO3))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))

  private val checkpointControllerMockLayer = ZLayer.succeed(checkpointControllerMock)

  private val createCheckpointServerEndpoint =
    createCheckpointEndpoint.zServerLogic(CheckpointController.createCheckpoint)

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[CheckpointController]))
      .whenServerEndpoint(createCheckpointServerEndpoint)
      .thenRunLogic()
      .backend()

    val request = basicRequest
      .post(uri"https://test.com/api/v1/createCheckpoint")
      .response(asJson[CheckpointDTO])

    suite("CreateCheckpointEndpointSuite")(
      test("Returns expected CheckpointDTO") {
        val response = request
          .body(checkpointDTO1)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(equalTo(Right(checkpointDTO1), StatusCode.Created))
      },
      test("Returns expected BadRequest") {
        val response = request
          .body(checkpointDTO2)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test("Returns expected InternalServerError") {
        val response = request
          .body(checkpointDTO3)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    )
  }.provide(
    checkpointControllerMockLayer
  )

}

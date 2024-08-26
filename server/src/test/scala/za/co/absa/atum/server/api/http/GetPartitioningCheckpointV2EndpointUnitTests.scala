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
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.CheckpointController
import za.co.absa.atum.server.model.NotFoundErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

import java.util.UUID

object GetPartitioningCheckpointV2EndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val checkpointControllerMock = mock(classOf[CheckpointController])

  when(checkpointControllerMock.getPartitioningCheckpointV2(1L, uuid1))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(checkpointV2DTO1, uuid1)))
  when(checkpointControllerMock.getPartitioningCheckpointV2(1L, uuid2))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("not found checkpoint for a given ID")))

  private val checkpointControllerMockLayer = ZLayer.succeed(checkpointControllerMock)

  private val getPartitioningCheckpointServerEndpointV2 = getPartitioningCheckpointEndpointV2
    .zServerLogic({ case (partitioningId: Long, checkpointId: UUID) =>
      CheckpointController.getPartitioningCheckpointV2(partitioningId, checkpointId)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[CheckpointController]))
      .whenServerEndpoint(getPartitioningCheckpointServerEndpointV2)
      .thenRunLogic()
      .backend()

    suite("GetPartitioningCheckpointV2EndpointSuite")(
      test("Returns an expected CheckpointV2DTO"){
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/checkpoints/$uuid1")
          .response(asJson[SingleSuccessResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(Right(SingleSuccessResponse(checkpointV2DTO1, uuid1)), StatusCode.Ok)
        )
      },
      test("Returns expected 404 when checkpoint for a given ID doesn't exist"){
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/checkpoints/$uuid2")
          .response(asJson[SingleSuccessResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      }
    )

  }.provide(
    checkpointControllerMockLayer
  )
}

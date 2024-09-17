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
import sttp.client3.circe._
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.{PartitioningSubmitDTO, PartitioningSubmitV2DTO, PartitioningWithIdDTO}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.PartitioningController
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.server.model.{ConflictErrorResponse, InternalServerErrorResponse}
import zio._
import zio.test.Assertion.equalTo
import zio.test._

object PostPartitioningEndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTO1))
    .thenReturn(ZIO.succeed((SingleSuccessResponse(partitioningWithIdDTO1, uuid1), "location")))

  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTO2))
    .thenReturn(ZIO.fail(ConflictErrorResponse("Partitioning already exists")))

  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTO3))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))

  private val createPartitioningEndpointMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val createPartitioningServerEndpoint =
    postPartitioningEndpointV2.zServerLogic({
      partitioningSubmitDTO: PartitioningSubmitV2DTO =>
        PartitioningController.postPartitioning(partitioningSubmitDTO)
      }
    )

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(createPartitioningServerEndpoint)
      .thenRunLogic()
      .backend()

    val request = basicRequest
      .post(uri"https://test.com/api/v2/partitionings")
      .response(asJson[SingleSuccessResponse[PartitioningWithIdDTO]])

    suite("CreatePartitioningEndpointSuite")(
      test("Returns expected PartitioningWithIdDTO and location header") {
        val response = request
          .body(partitioningSubmitV2DTO1)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)
        val header = response.map(_.header("Location"))

        assertZIO(body <&> statusCode <&> header)(
          equalTo(Right(SingleSuccessResponse(partitioningWithIdDTO1, uuid1)), StatusCode.Created, Some("location"))
        )
      },
      test("Returns expected BadRequest") {
        val response = request
          .body(partitioningSubmitV2DTO2)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.Conflict))
      },
      test("Returns expected InternalServerError") {
        val response = request
          .body(partitioningSubmitV2DTO3)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    )
  }.provide(
    createPartitioningEndpointMockLayer
  )

}

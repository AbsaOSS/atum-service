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
import za.co.absa.atum.model.dto.AtumContextDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.PartitioningController
import za.co.absa.atum.server.model.{GeneralErrorResponse, InternalServerErrorResponse}
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio._
import zio.test.Assertion.equalTo
import zio.test._

object CreatePartitioningEndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val createPartitioningEndpointMock = mock(classOf[PartitioningController])

  when(createPartitioningEndpointMock.createPartitioningIfNotExistsV2(partitioningSubmitDTO1))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(createAtumContextDTO(partitioningSubmitDTO1), uuid)))
  when(createPartitioningEndpointMock.createPartitioningIfNotExistsV2(partitioningSubmitDTO2))
    .thenReturn(ZIO.fail(GeneralErrorResponse("error")))
  when(createPartitioningEndpointMock.createPartitioningIfNotExistsV2(partitioningSubmitDTO3))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))

  private val createPartitioningEndpointMockLayer = ZLayer.succeed(createPartitioningEndpointMock)

  private val createPartitioningServerEndpoint =
    createPartitioningEndpointV2.zServerLogic(PartitioningController.createPartitioningIfNotExistsV2)

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(createPartitioningServerEndpoint)
      .thenRunLogic()
      .backend()

    val request = basicRequest
      .post(uri"https://test.com/api/v2/create-partitioning")
      .response(asJson[SingleSuccessResponse[AtumContextDTO]])

    suite("CreatePartitioningEndpointSuite")(
      test("Returns expected AtumContextDTO") {
        val response = request
          .body(partitioningSubmitDTO1)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(Right(SingleSuccessResponse(createAtumContextDTO(partitioningSubmitDTO1), uuid)), StatusCode.Ok)
        )
      },
      test("Returns expected BadRequest") {
        val response = request
          .body(partitioningSubmitDTO2)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test("Returns expected InternalServerError") {
        val response = request
          .body(partitioningSubmitDTO3)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    )
  }.provide(
    createPartitioningEndpointMockLayer
  )

}

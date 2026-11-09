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
import za.co.absa.atum.model.dto.ParentPartitioningDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.MultiSuccessResponse
import za.co.absa.atum.model.envelopes.{InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import za.co.absa.atum.server.api.v2.http.Endpoints.getPartitioningParentsEndpoint
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object GetPartitioningParentsEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioningParents(1111L))
    .thenReturn(
      ZIO.succeed(
        MultiSuccessResponse(Seq.empty[ParentPartitioningDTO], uuid1)
      )
    )
  when(partitioningControllerMock.getPartitioningParents(7777L))
    .thenReturn(
      ZIO.succeed(
        MultiSuccessResponse(
          Seq(parentPartitioningDTO1, parentPartitioningDTO2),
          uuid1
        )
      )
    )
  when(partitioningControllerMock.getPartitioningParents(8888L))
    .thenReturn(
      ZIO.fail(
        NotFoundErrorResponse("Partitioning not found")
      )
    )
  when(partitioningControllerMock.getPartitioningParents(9999L))
    .thenReturn(
      ZIO.fail(
        InternalServerErrorResponse("internal server error")
      )
    )

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getPartitioningParentsServerEndpoint =
    getPartitioningParentsEndpoint.zServerLogic(PartitioningController.getPartitioningParents)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningParentsServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("GetParentsEndpointSuite")(
      test("Returns an expected empty MultiSuccessResponse when partitioning has no parents") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1111/parents")
          .response(asJson[MultiSuccessResponse[ParentPartitioningDTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(MultiSuccessResponse(Seq.empty[ParentPartitioningDTO], uuid1)),
            StatusCode.Ok
          )
        )
      },
      test("Returns all direct parents of a partitioning in MultiSuccessResponse") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/7777/parents")
          .response(asJson[MultiSuccessResponse[ParentPartitioningDTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(
              MultiSuccessResponse(
                Seq(parentPartitioningDTO1, parentPartitioningDTO2),
                uuid1
              )
            ),
            StatusCode.Ok
          )
        )
      },
      test("Returns a NotFoundErrorResponse when partitioning does not exist") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/8888/parents")
          .response(asJson[NotFoundErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns an InternalServerErrorResponse on unexpected error") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/9999/parents")
          .response(asJson[InternalServerErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    )

  }.provide(partitioningControllerMockLayer)

}

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
import za.co.absa.atum.model.dto.ChildPartitioningDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.MultiSuccessResponse
import za.co.absa.atum.model.envelopes.{GeneralErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import za.co.absa.atum.server.api.v2.http.Endpoints.getPartitioningChildrenEndpoint
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object GetPartitioningChildrenEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioningChildren(1111L))
    .thenReturn(
      ZIO.succeed(
        MultiSuccessResponse(Seq.empty[ChildPartitioningDTO], uuid1)
      )
    )
  when(partitioningControllerMock.getPartitioningChildren(7777L))
    .thenReturn(
      ZIO.succeed(
        MultiSuccessResponse(
          Seq(childPartitioningDTO1, childPartitioningDTO2),
          uuid1
        )
      )
    )
  when(partitioningControllerMock.getPartitioningChildren(8888L))
    .thenReturn(
      ZIO.fail(
        NotFoundErrorResponse("Partitioning not found")
      )
    )
  when(partitioningControllerMock.getPartitioningChildren(9999L))
    .thenReturn(
      ZIO.fail(
        InternalServerErrorResponse("internal server error")
      )
    )
  when(partitioningControllerMock.getPartitioningChildren(6666L))
    .thenReturn(
      ZIO.fail(
        GeneralErrorResponse("error")
      )
    )

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getPartitioningChildrenServerEndpoint =
    getPartitioningChildrenEndpoint.zServerLogic(PartitioningController.getPartitioningChildren)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningChildrenServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("GetPartitioningChildrenEndpointSuite")(
      test("Returns an expected empty MultiSuccessResponse when partitioning has no children") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1111/children")
          .response(asJson[MultiSuccessResponse[ChildPartitioningDTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(MultiSuccessResponse(Seq.empty[ChildPartitioningDTO], uuid1)),
            StatusCode.Ok
          )
        )
      },
      test("Returns all direct children of a partitioning in MultiSuccessResponse") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/7777/children")
          .response(asJson[MultiSuccessResponse[ChildPartitioningDTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(
            Right(
              MultiSuccessResponse(
                Seq(childPartitioningDTO1, childPartitioningDTO2),
                uuid1
              )
            ),
            StatusCode.Ok
          )
        )
      },
      test("Returns a NotFoundErrorResponse when partitioning does not exist") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/8888/children")
          .response(asJson[NotFoundErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns an InternalServerErrorResponse on unexpected error") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/9999/children")
          .response(asJson[InternalServerErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      },
      test("Returns a BadRequest for a general error") {
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/6666/children")
          .response(asJson[GeneralErrorResponse])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      }
    )

  }.provide(partitioningControllerMockLayer)

}

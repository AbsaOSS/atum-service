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
import sttp.client3.circe._
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.PartitioningParentsSubmitDTO
import za.co.absa.atum.model.envelopes.{
  ConflictErrorResponse,
  GeneralErrorResponse,
  InternalServerErrorResponse,
  NotFoundErrorResponse
}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import za.co.absa.atum.server.api.v2.http.Endpoints.postPartitioningParentsEndpoint
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object PostPartitioningParentsEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock: PartitioningController = mock(classOf[PartitioningController])

  when(partitioningControllerMock.postPartitioningParents(1L, partitioningParentsSubmitDTO1))
    .thenReturn(ZIO.unit)
  when(partitioningControllerMock.postPartitioningParents(2L, partitioningParentsSubmitDTO1))
    .thenReturn(ZIO.fail(GeneralErrorResponse("error")))
  when(partitioningControllerMock.postPartitioningParents(3L, partitioningParentsSubmitDTO1))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))
  when(partitioningControllerMock.postPartitioningParents(4L, partitioningParentsSubmitDTO1))
    .thenReturn(ZIO.fail(ConflictErrorResponse("error")))
  when(partitioningControllerMock.postPartitioningParents(0L, partitioningParentsSubmitDTO1))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("Child Partitioning not found")))

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val postPartitioningParentsEndpointLogic = postPartitioningParentsEndpoint
    .zServerLogic({ case (partitioningId: Long, submitDTO: PartitioningParentsSubmitDTO) =>
      PartitioningController.postPartitioningParents(partitioningId, submitDTO)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(postPartitioningParentsEndpointLogic)
      .thenRunLogic()
      .backend()

    val request = basicRequest
      .post(uri"https://test.com/api/v2/partitionings/1/parents")

    suite("PostPartitioningParentsEndpointUnitTests")(
      test("Returns 204 NoContent on success") {
        val response = request
          .body(partitioningParentsSubmitDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)
        assertZIO(statusCode)(equalTo(StatusCode.NoContent))
      },
      test("Returns 400 BadRequest for a general error") {
        val response = basicRequest
          .post(uri"https://test.com/api/v2/partitionings/2/parents")
          .body(partitioningParentsSubmitDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)
        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test("Returns 409 Conflict when cycle/conflict detected") {
        val response = basicRequest
          .post(uri"https://test.com/api/v2/partitionings/4/parents")
          .body(partitioningParentsSubmitDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)
        assertZIO(statusCode)(equalTo(StatusCode.Conflict))
      },
      test("Returns 404 NotFound when partitioning does not exist") {
        val response = basicRequest
          .post(uri"https://test.com/api/v2/partitionings/0/parents")
          .body(partitioningParentsSubmitDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)
        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns 500 InternalServerError on unexpected error") {
        val response = basicRequest
          .post(uri"https://test.com/api/v2/partitionings/3/parents")
          .body(partitioningParentsSubmitDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)
        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    )

  }.provide(partitioningControllerMockLayer)

}

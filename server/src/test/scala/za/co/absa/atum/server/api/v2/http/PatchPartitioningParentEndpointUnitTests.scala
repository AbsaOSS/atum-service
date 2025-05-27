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
import za.co.absa.atum.model.dto.PartitioningParentPatchDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.envelopes.{ConflictErrorResponse, GeneralErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import za.co.absa.atum.server.api.v2.http.Endpoints.patchPartitioningParentEndpoint
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object PatchPartitioningParentEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock: PartitioningController = mock(classOf[PartitioningController])

  when(partitioningControllerMock.patchPartitioningParent(1L, partitioningParentPatchDTO1))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(partitioningParentPatchDTO1, uuid1)))
  when(partitioningControllerMock.patchPartitioningParent(1L, partitioningParentPatchDTO2))
    .thenReturn(ZIO.fail(GeneralErrorResponse("error")))
  when(partitioningControllerMock.patchPartitioningParent(1L, partitioningParentPatchDTO3))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))
  when(partitioningControllerMock.patchPartitioningParent(1L, partitioningParentPatchDTO4))
    .thenReturn(ZIO.fail(ConflictErrorResponse("error")))
  when(partitioningControllerMock.patchPartitioningParent(0L, partitioningParentPatchDTO4))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("Child Partitioning not found")))

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val patchPartitioningParentEndpointLogic = patchPartitioningParentEndpoint
    .zServerLogic({ case (partitioningId: Long, partitioningParentPatchDTO: PartitioningParentPatchDTO) =>
      PartitioningController.patchPartitioningParent(partitioningId, partitioningParentPatchDTO)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(patchPartitioningParentEndpointLogic)
      .thenRunLogic()
      .backend()

    val request = basicRequest
      .patch(uri"https://test.com/api/v2/partitionings/1/ancestors")
      .response(asJson[SingleSuccessResponse[PartitioningParentPatchDTO]])

    suite("PatchPartitioningParentEndpointUnitTests")(
      test("Returns expected PartitioningParentPatchDTO") {
        val response = request
          .body(partitioningParentPatchDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)
        assertZIO(statusCode)(equalTo(StatusCode.NoContent))
      },
      test("Returns expected BadRequest") {
        val response = request
          .body(partitioningParentPatchDTO2)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.BadRequest))
      },
      test("Returns expected InternalServerError") {
        val response = request
          .body(partitioningParentPatchDTO3)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      },
      test("Returns expected ConflictError") {
        val response = request
          .body(partitioningParentPatchDTO4)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.Conflict))
      },
      test("Returns expected NotFound") {
        val response = request
          .patch(uri"https://test.com/api/v2/partitionings/0/ancestors")
          .body(partitioningParentPatchDTO4)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      }
    )
  }.provide(
    partitioningControllerMockLayer
  )
}

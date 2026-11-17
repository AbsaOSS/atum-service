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
import sttp.client3.{UriContext, asString, basicRequest}
import sttp.model.{MediaType, StatusCode}
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.PartitioningParentPatchDTO
import za.co.absa.atum.model.envelopes.{ConflictErrorResponse, GeneralErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import za.co.absa.atum.server.api.v2.http.Endpoints.patchPartitioningParentEndpoint
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO, ZLayer}

object PatchPartitioningParentEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock: PartitioningController = mock(classOf[PartitioningController])

  when(partitioningControllerMock.patchPartitioningParent(1L, partitioningParentPatchDTO1))
    .thenReturn(ZIO.unit)
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
    .zServerLogic((PartitioningController.patchPartitioningParent _).tupled)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(patchPartitioningParentEndpointLogic)
      .thenRunLogic()
      .backend()

    val baseRequest = basicRequest
      .patch(uri"https://test.com/api/v2/partitionings/1/ancestors")

    suite("PatchPartitioningParentEndpointUnitTests")(
      test("Returns 204 NoContent with empty body") {
        for {
          response <- baseRequest
            .body(partitioningParentPatchDTO1)
            .response(asString)
            .send(backendStub)
        } yield assertTrue(
          response.code == StatusCode.NoContent,
          response.body == Right("")
        )
      },
      test("Returns 400 BadRequest when copyMeasurements and copyAdditionalData are omitted") {
        for {
          response <- baseRequest
            .contentType(MediaType.ApplicationJson)
            .body("""{"parentPartitioningId": 1, "author": "author"}""")
            .response(asString)
            .send(backendStub)
        } yield assertTrue(response.code == StatusCode.BadRequest)
      },
      test("Returns expected BadRequest with decoded GeneralErrorResponse") {
        for {
          response <- baseRequest
            .body(partitioningParentPatchDTO2)
            .response(asJsonAlways[GeneralErrorResponse])
            .send(backendStub)
        } yield assertTrue(
          response.code == StatusCode.BadRequest,
          response.body.map(_.message) == Right("error")
        )
      },
      test("Returns expected InternalServerError with decoded InternalServerErrorResponse") {
        for {
          response <- baseRequest
            .body(partitioningParentPatchDTO3)
            .response(asJsonAlways[InternalServerErrorResponse])
            .send(backendStub)
        } yield assertTrue(
          response.code == StatusCode.InternalServerError,
          response.body.map(_.message) == Right("error")
        )
      },
      test("Returns expected ConflictError with decoded ConflictErrorResponse") {
        for {
          response <- baseRequest
            .body(partitioningParentPatchDTO4)
            .response(asJsonAlways[ConflictErrorResponse])
            .send(backendStub)
        } yield assertTrue(
          response.code == StatusCode.Conflict,
          response.body.map(_.message) == Right("error")
        )
      },
      test("Returns expected NotFound with decoded NotFoundErrorResponse") {
        for {
          response <- basicRequest
            .patch(uri"https://test.com/api/v2/partitionings/0/ancestors")
            .body(partitioningParentPatchDTO4)
            .response(asJsonAlways[NotFoundErrorResponse])
            .send(backendStub)
        } yield assertTrue(
          response.code == StatusCode.NotFound,
          response.body.map(_.message) == Right("Child Partitioning not found")
        )
      }
    )
  }.provide(
    partitioningControllerMockLayer
  )
}

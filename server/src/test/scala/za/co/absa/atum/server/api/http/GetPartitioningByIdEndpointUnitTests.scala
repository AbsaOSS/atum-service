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
import sttp.client3._
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import sttp.client3.circe._
import sttp.model.StatusCode
import za.co.absa.atum.model.dto.PartitioningWithIdDTO
import za.co.absa.atum.model.envelopes.{InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.PartitioningController
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import zio.{Scope, ZIO, ZLayer}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object GetPartitioningByIdEndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioningByIdV2(1L))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(partitioningWithIdDTO1)))
  when(partitioningControllerMock.getPartitioningByIdV2(2L))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))
  when(partitioningControllerMock.getPartitioningByIdV2(3L))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("boom!")))

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getPartitioningServerEndpoint =
    getPartitioningByIdEndpointV2.zServerLogic(PartitioningController.getPartitioningByIdV2)

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningServerEndpoint)
      .thenRunLogic()
      .backend()

    def createBasicRequest(id: Long): RequestT[Identity, Either[ResponseException[String, io.circe.Error], SingleSuccessResponse[PartitioningWithIdDTO]], Any] = {
      basicRequest
        .get(uri"https://test.com/api/v2/partitionings/$id")
        .response(asJson[SingleSuccessResponse[PartitioningWithIdDTO]])
    }

    suite("GetPartitioningEndpointSuite")(
      test("Returns expected PartitioningWithIdDTO") {
        for {
          response <- createBasicRequest(1L).send(backendStub)
          body <- ZIO.fromEither(response.body)
          statusCode = response.code
        } yield {
          assertTrue(body.data == SingleSuccessResponse(partitioningWithIdDTO1).data, statusCode == StatusCode.Ok)
        }
      },
      test("Returns expected general error") {
        for {
          response <- createBasicRequest(2L).send(backendStub)
          statusCode = response.code
        } yield {
          assertTrue(statusCode == StatusCode.InternalServerError)
        }
      },
      test("Returns expected not found error") {
        for {
          response <- createBasicRequest(3L).send(backendStub)
          statusCode = response.code
        } yield {
          assertTrue(statusCode == StatusCode.NotFound)
        }
      }
    )
  }.provide(partitioningControllerMockLayer)
}

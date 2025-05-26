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
import sttp.client3._
import sttp.client3.circe._
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.MeasureDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.MultiSuccessResponse
import za.co.absa.atum.model.envelopes.{GeneralErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import zio._
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object GetPartitioningMeasuresEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioningMeasures(1L))
    .thenReturn(ZIO.succeed(MultiSuccessResponse(Seq(measureDTO1, measureDTO2), uuid1)))
  when(partitioningControllerMock.getPartitioningMeasures(2L))
    .thenReturn(ZIO.fail(GeneralErrorResponse("error")))
  when(partitioningControllerMock.getPartitioningMeasures(3L))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("boom!")))

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getPartitioningMeasuresServerEndpoint =
    Endpoints.getPartitioningMeasuresEndpoint.zServerLogic({partitioningId: Long =>
      PartitioningController.getPartitioningMeasures(partitioningId)
    })

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningMeasuresServerEndpoint)
      .thenRunLogic()
      .backend()

    def createBasicRequest(id: Long): RequestT[Identity, Either[ResponseException[String, io.circe.Error], MultiSuccessResponse[MeasureDTO]], Any] = {
      basicRequest
        .get(uri"https://test.com/api/v2/partitionings/$id/measures")
        .response(asJson[MultiSuccessResponse[MeasureDTO]])
    }

    suite("GetPartitioningMeasuresEndpointSuite")(
      test("Returns expected MeasureDTO") {
        for {
          response <- createBasicRequest(1L).send(backendStub)
          body <- ZIO.fromEither(response.body)
          statusCode = response.code
        } yield {
          assertTrue(body.data == MultiSuccessResponse(Seq(measureDTO1, measureDTO2), uuid1).data, statusCode == StatusCode.Ok)
        }
      },
      test("Returns expected general error") {
        for {
          response <- createBasicRequest(2L).send(backendStub)
          statusCode = response.code
        } yield {
          assertTrue(statusCode == StatusCode.BadRequest)
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


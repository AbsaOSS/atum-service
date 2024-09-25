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

import io.circe
import org.mockito.Mockito.{mock, when}
import sttp.client3.circe.asJson
import sttp.client3.{Identity, RequestT, ResponseException, UriContext, basicRequest}
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.{PartitioningDTO, PartitioningWithIdDTO}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.PartitioningController
import za.co.absa.atum.server.model.NotFoundErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio.test.Assertion.equalTo
import zio.{Scope, ZIO, ZLayer}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}

object GetPartitioningEndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.getPartitioning(encodePartitioningDTO(partitioningDTO1)))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(partitioningWithIdDTO1, uuid1)))
  when(partitioningControllerMock.getPartitioning(encodePartitioningDTO(partitioningDTO2)))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("Partitioning not found")))

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val getPartitioningServerEndpoint =
    getPartitioningEndpointV2.zServerLogic(PartitioningController.getPartitioning)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(getPartitioningServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("GetPartitioningEndpointSuite")(
      test("Returns expected SingleSuccessResponse[PartitioningWithIdDTO]") {
        val response = getRequestForPartitioningDTO(partitioningDTO1).send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        val expectedResult = SingleSuccessResponse(partitioningWithIdDTO1, uuid1)

        assertZIO(body <&> statusCode)(equalTo(Right(expectedResult), StatusCode.Ok))
      }
    )

  }.provide(partitioningControllerMockLayer)

  private def getRequestForPartitioningDTO(partitioningDTO: PartitioningDTO): RequestT[Identity, Either[
    ResponseException[String, circe.Error],
    SingleSuccessResponse[PartitioningWithIdDTO]
  ], Any] = {
    val baseUrl = uri"https://test.com/api/v2/partitionings"
    val encodedPartitioning = encodePartitioningDTO(partitioningDTO)

    basicRequest
      .get(baseUrl.addParam("partitioning", encodedPartitioning))
      .response(asJson[SingleSuccessResponse[PartitioningWithIdDTO]])
  }

}

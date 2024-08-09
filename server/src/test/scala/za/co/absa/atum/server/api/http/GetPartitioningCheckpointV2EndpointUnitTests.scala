package za.co.absa.atum.server.api.http

import org.mockito.Mockito.{mock, when}
import sttp.client3.circe.asJson
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.CheckpointController
import za.co.absa.atum.server.model.NotFoundErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object GetPartitioningCheckpointV2EndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val checkpointControllerMock = mock(classOf[CheckpointController])

  when(checkpointControllerMock.getPartitioningCheckpointV2(1L, "abc"))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(checkpointV2DTO1, uuid)))
  when(checkpointControllerMock.getPartitioningCheckpointV2(1L, "def"))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("not found checkpoint for a given ID")))

  private val checkpointControllerMockLayer = ZLayer.succeed(checkpointControllerMock)

  private val getPartitioningCheckpointServerEndpointV2 = getPartitioningCheckpointEndpointV2
    .zServerLogic({ case (partitioningId: Long, checkpointId: String) =>
      CheckpointController.getPartitioningCheckpointV2(partitioningId, checkpointId)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[CheckpointController]))
      .whenServerEndpoint(getPartitioningCheckpointServerEndpointV2)
      .thenRunLogic()
      .backend()

    suite("GetPartitioningCheckpointV2EndpointSuite")(
      test("Returns an expected CheckpointV2DTO"){
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/checkpoints/abc")
          .response(asJson[SingleSuccessResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(Right(SingleSuccessResponse(checkpointV2DTO1, uuid)), StatusCode.Ok)
        )
      },
      test("Returns expected 404 when checkpoint for a given ID doesn't exist"){
        val request = basicRequest
          .get(uri"https://test.com/api/v2/partitionings/1/checkpoints/def")
          .response(asJson[SingleSuccessResponse[CheckpointV2DTO]])

        val response = request
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      }
    )

  }.provide(
    checkpointControllerMockLayer
  )
}

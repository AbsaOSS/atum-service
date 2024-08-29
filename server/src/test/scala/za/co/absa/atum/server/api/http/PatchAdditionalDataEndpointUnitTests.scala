package za.co.absa.atum.server.api.http

import io.circe
import sttp.client3.circe._
import org.mockito.Mockito.{mock, when}
import sttp.client3.circe.asJson
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Identity, RequestT, ResponseException, UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataPatchDTO}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.PartitioningController
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.server.model.{InternalServerErrorResponse, NotFoundErrorResponse}
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO, ZLayer}

object PatchAdditionalDataEndpointUnitTests extends ZIOSpecDefault with Endpoints with TestData {

  private val partitioningControllerMock: PartitioningController = mock(classOf[PartitioningController])

  when(partitioningControllerMock.patchPartitioningAdditionalDataV2(1L, additionalDataPatchDTO1))
    .thenReturn(ZIO.succeed(SingleSuccessResponse(additionalDataDTO1, uuid1)))
  when(partitioningControllerMock.patchPartitioningAdditionalDataV2(0L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("error")))
  when(partitioningControllerMock.patchPartitioningAdditionalDataV2(2L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))

  private val partitioningControllerMockLayer = ZLayer.succeed(partitioningControllerMock)

  private val patchAdditionalDataEndpointLogic = patchPartitioningAdditionalDataEndpointV2
    .zServerLogic({ case (partitioningId: Long, additionalDataPatchDTO: AdditionalDataPatchDTO) =>
      PartitioningController.patchPartitioningAdditionalDataV2(partitioningId, additionalDataPatchDTO)
    })

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(patchAdditionalDataEndpointLogic)
      .thenRunLogic()
      .backend()

    suite("PatchAdditionalDataEndpointUnitTests")(
      test("Returns expected AdditionalDataDTO") {
        val response = patchRequestForId(1L)
          .body(additionalDataPatchDTO1)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(Right(SingleSuccessResponse(additionalDataDTO1, uuid1)), StatusCode.Ok)
        )
      },
      test("Returns NotFoundErrorResponse") {
        val response = patchRequestForId(0L)
          .body(additionalDataPatchDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.NotFound))
      },
      test("Returns InternalServerErrorResponse") {
        val response = patchRequestForId(2L)
          .body(additionalDataPatchDTO1)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    )

  }.provide(partitioningControllerMockLayer)

  private def patchRequestForId(id: Long): RequestT[Identity, Either[
    ResponseException[String, circe.Error],
    SingleSuccessResponse[AdditionalDataDTO]
  ], Any] = {
    basicRequest
      .patch(uri"https://test.com/api/v2/partitionings/$id/additional-data")
      .response(asJson[SingleSuccessResponse[AdditionalDataDTO]])
  }

}

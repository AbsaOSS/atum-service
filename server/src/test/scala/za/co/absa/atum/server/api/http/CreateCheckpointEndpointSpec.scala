package za.co.absa.atum.server.api.http

import org.junit.runner.RunWith
import org.mockito.Mockito.{mock, when}
import sttp.client3._
import sttp.client3.testing.SttpBackendStub
import sttp.client3.playJson._
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.CheckpointController
import za.co.absa.atum.server.model.{GeneralErrorResponse, InternalServerErrorResponse}
import zio.test.junit.ZTestJUnitRunner
import zio.test._
import zio._
import za.co.absa.atum.server.model.PlayJsonImplicits.{readsCheckpointDTO, writesCheckpointDTO}

//@RunWith(classOf[ZTestJUnitRunner])
object CreateCheckpointEndpointSpec extends ZIOSpecDefault with Endpoints with TestData {

  private val checkpointControllerMock = mock(classOf[CheckpointController])

  when(checkpointControllerMock.createCheckpoint(checkpointDTO1)).thenReturn(ZIO.succeed(checkpointDTO1))
  when(checkpointControllerMock.createCheckpoint(checkpointDTO2)).thenReturn(ZIO.fail(GeneralErrorResponse("error")))
  when(checkpointControllerMock.createCheckpoint(checkpointDTO3)).thenReturn(ZIO.fail(InternalServerErrorResponse("error")))

  private val checkpointControllerMockLayer = ZLayer.succeed(checkpointControllerMock)

  private val createCheckpointServerEndpoint = createCheckpointEndpoint.zServerLogic(CheckpointController.createCheckpoint)

  def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("CreateCheckpointEndpointSuite")(
      test("simple test") {
        val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[CheckpointController]))
          .whenServerEndpoint(createCheckpointServerEndpoint)
          .thenRunLogic()
          .backend()

        val response = basicRequest
          .post(uri"https://test.com/api/v1/createCheckpoint")
          .body(checkpointDTO1)
          .response(asJson[CheckpointDTO])
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body)(Assertion.equalTo(Right(checkpointDTO1))) *>
          assertZIO(statusCode)(Assertion.equalTo(StatusCode.Created))
      }
    )
  }.provide(
    checkpointControllerMockLayer
  )

}

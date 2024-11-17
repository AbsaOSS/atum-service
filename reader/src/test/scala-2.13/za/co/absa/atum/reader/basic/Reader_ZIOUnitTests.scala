package za.co.absa.atum.reader.basic

import io.circe.Decoder
import sttp.capabilities.WebSockets
import sttp.client3.SttpBackend
import sttp.client3.impl.zio.RIOMonadAsyncError
import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadError
import za.co.absa.atum.model.dto.PartitionDTO
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax
import za.co.absa.atum.reader.basic.RequestResult.RequestResult
import za.co.absa.atum.reader.server.ServerConfig
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Scope, Task}

object Reader_ZIOUnitTests extends ZIOSpecDefault {
  private implicit val serverConfig: ServerConfig = ServerConfig("http://localhost:8080")

  private class ReaderForTest[F[_]](implicit serverConfig: ServerConfig, backend: SttpBackend[F, Any], ev: MonadError[F])
    extends Reader {
    override def getQuery[R: Decoder](endpointUri: String, params: Map[String, String]): F[RequestResult[R]] = super.getQuery(endpointUri, params)
  }

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("Reader_ZIO")(
      test("Using ZIO based backend") {
        import za.co.absa.atum.reader.implicits.zio.ZIOMonad

        val partitionDTO = PartitionDTO("someKey", "someValue")

        implicit val server: SttpBackendStub[Task, WebSockets] = SttpBackendStub[Task, WebSockets](new RIOMonadAsyncError[Any])
          .whenAnyRequest.thenRespond(partitionDTO.asJsonString)

        val reader = new ReaderForTest
        val expected:  RequestResult[PartitionDTO] = Right(partitionDTO)
        for {
          result <- reader.getQuery[PartitionDTO]("test/", Map.empty)
        } yield assertTrue(result == expected)
      }
    )
  }

}

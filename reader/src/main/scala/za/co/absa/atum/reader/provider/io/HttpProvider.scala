package za.co.absa.atum.reader.provider.io

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}
import sttp.client3.Response
import sttp.client3.armeria.cats.ArmeriaCatsBackend
import za.co.absa.atum.reader.provider.AbstractHttpProvider

import scala.util.Try

class HttpProvider(serverUrl: String) extends AbstractHttpProvider[IO](serverUrl) {

  def this(config: Config = ConfigFactory.load()) = {
    this(AbstractHttpProvider.atumServerUrl(config ))
  }

  override protected def executeRequest(requestFnc: RequestFunction): IO[Response[Either[String, String]]] = {
    ArmeriaCatsBackend
      .resource[IO]()
      .use(requestFnc)
  }

  override protected def mapResponse[R](
                                         response: IO[Response[Either[String, String]]],
                                         mapperFnc: ResponseMapperFunction[R]): IO[Try[R]] = {
    response.map(mapperFnc)
  }
}

object HttpProvider {
  lazy implicit val httpProvider: HttpProvider = new HttpProvider()
}

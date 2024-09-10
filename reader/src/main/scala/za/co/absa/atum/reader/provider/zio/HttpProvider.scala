package za.co.absa.atum.reader.provider.zio

import com.typesafe.config.{Config, ConfigFactory}
import sttp.client3.Response
import sttp.client3.armeria.zio.ArmeriaZioBackend
import za.co.absa.atum.reader.provider.AbstractHttpProvider
import zio.ZIO

import scala.util.Try

class HttpProvider(serverUrl: String) extends AbstractHttpProvider[ZIO](serverUrl) {

  def this(config: Config = ConfigFactory.load()) = {
    this(AbstractHttpProvider.atumServerUrl(config ))
  }



  override protected def executeRequest(requestFnc: RequestFunction): ZIO[Response[Either[String, String]]] = {
    ArmeriaZioBackend.usingDefaultClient().map(requestFnc)
  }

  override protected def mapResponse[R](response: ZIO[Response[Either[String, String]]], mapperFnc: ResponseMapperFunction[R]): ZIO[Try[R]] = ???
} //TODO

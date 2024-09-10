/*
 * Copyright 2024 ABSA Group Limited
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

package za.co.absa.atum.reader.provider

import _root_.io.circe.parser.decode
import _root_.io.circe.Decoder
import com.typesafe.config.Config
import sttp.client3.{Response, SttpBackend, UriContext, basicRequest}
import za.co.absa.atum.reader.exceptions.RequestException

import scala.util.{Failure, Try}

/**
 * A HttpProvider is a component that is responsible for providing teh data to readers using REST API
 * @tparam F
 */
abstract class AbstractHttpProvider[F[_]](val serverUrl: String) extends Provider[F] {
  type RequestFunction = SttpBackend[F, Any] => F[Response[Either[String, String]]]
  type ResponseMapperFunction[R] = Response[Either[String, String]] => Try[R]

  protected def executeRequest(requestFnc: RequestFunction): F[Response[Either[String, String]]]
  protected def mapResponse[R](response: F[Response[Either[String, String]]], mapperFnc: ResponseMapperFunction[R]): F[Try[R]]

  protected def query[R: Decoder](endpointUri: String): F[Try[R]] = {
    val endpointToQuery = serverUrl + endpointUri
    val request = basicRequest
      .get(uri"$endpointToQuery")
    val response = executeRequest(request.send(_))
    mapResponse(response, responseMapperFunction[R])
  }

  private def responseMapperFunction[R: Decoder](response: Response[Either[String, String]]): Try[R] = {
    response.body match {
      case Left(error) => Failure(RequestException(response.statusText, error, response.code, response.request))
      case Right(body) => decode[R](body).toTry
    }
  }

}

object AbstractHttpProvider {
  final val UrlKey = "atum.server.url"

  def atumServerUrl(config: Config): String = {
    config.getString(UrlKey)
  }
}

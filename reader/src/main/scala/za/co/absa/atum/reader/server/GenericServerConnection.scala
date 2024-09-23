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

package za.co.absa.atum.reader.server

import _root_.io.circe.parser.decode
import _root_.io.circe.Decoder
import cats.Monad
import cats.implicits.toFunctorOps
import com.typesafe.config.Config
import sttp.client3.{Identity, RequestT, Response, UriContext, basicRequest}
import za.co.absa.atum.reader.exceptions.RequestException
import za.co.absa.atum.reader.server.GenericServerConnection.ReaderResponse

import scala.util.{Failure, Try}

/**
 * A HttpProvider is a component that is responsible for providing teh data to readers using REST API
 * @tparam F
 */
abstract class GenericServerConnection[F[_]: Monad](val serverUrl: String) {

  protected def executeRequest(request: RequestT[Identity, Either[String, String], Any]): F[ReaderResponse]

  def query[R: Decoder](endpointUri: String): F[Try[R]] = {
    val endpointToQuery = serverUrl + endpointUri
    val request = basicRequest
      .get(uri"$endpointToQuery")
    val response = executeRequest(request)
    // using map instead of Circe's `asJson` to have own exception from a failed response
    response.map { responseData =>
      responseData.body match {
        case Left(error) => Failure(RequestException(responseData.statusText, error, responseData.code, responseData.request))
        case Right(body) => decode[R](body).toTry
      }
    }
  }

}

object GenericServerConnection {
  final val UrlKey = "atum.server.url"

  type ReaderResponse = Response[Either[String, String]]

  def atumServerUrl(config: Config): String = {
    config.getString(UrlKey)
  }
}

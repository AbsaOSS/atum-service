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

import _root_.io.circe.Decoder
import _root_.io.circe.{Error => circeError}
import com.typesafe.config.Config
import sttp.client3.{Identity, RequestT, ResponseException, basicRequest}
import sttp.model.Uri
import sttp.client3.circe._

import za.co.absa.atum.model.envelopes.ErrorResponse
import za.co.absa.atum.reader.server.GenericServerConnection.RequestResult

abstract class GenericServerConnection[F[_]](val serverUrl: String) {

  protected def executeRequest[R](request: RequestT[Identity, RequestResult[R], Any]): F[RequestResult[R]]

  def getQuery[R: Decoder](endpointUri: String, params: Map[String, String] = Map.empty): F[RequestResult[R]] = {
    val endpointToQuery = serverUrl + endpointUri
    val uri = Uri.unsafeParse(endpointToQuery).addParams(params)
    val request: RequestT[Identity, RequestResult[R], Any] = basicRequest
      .get(uri)
      .response(asJsonEither[ErrorResponse, R])
    executeRequest(request)
  }

  def close(): F[Unit]

}

object GenericServerConnection {
  final val UrlKey = "atum.server.url"

  type RequestResult[R] = Either[ResponseException[ErrorResponse, circeError], R]

  def atumServerUrl(config: Config): String = {
    config.getString(UrlKey)
  }
}

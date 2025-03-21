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

package za.co.absa.atum.reader.core

import io.circe.Decoder
import sttp.client3.{Identity, RequestT, ResponseException, SttpBackend, basicRequest}
import sttp.client3.circe.asJson
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.monad.syntax._
import za.co.absa.atum.reader.core.RequestResult._
import za.co.absa.atum.reader.server.ServerConfig
import za.co.absa.atum.reader.exceptions.RequestException.CirceError

/**
 * Reader is a base class for reading data from a remote server.
 * @param serverConfig    - the configuration how to reach the Atum server
 * @param backend         - sttp backend to use to send requests
 * @tparam F              - the monadic effect used to get the data (e.g. Future, IO, Task, etc.)
 *                        the context bind for the F type is MonadError to allow not just map, flatMap but eventually
 *                        also error handling easily on a higher level
 */
abstract class Reader[F[_]: MonadError](implicit val serverConfig: ServerConfig, val backend: SttpBackend[F, Any]) {

  protected def mapRequestResultF[I, O](requestResult: RequestResult[I], f: I => F[RequestResult[O]]): F[RequestResult[O]] = requestResult match {
    case Right(b) => f(b)
    case Left(a) => MonadError[F].unit(Left(a))
  }




  protected def getQuery[R: Decoder](endpointUri: String, params: Map[String, String] = Map.empty): F[RequestResult[R]] = {
    val endpointToQuery = serverConfig.host + endpointUri
    val uri = Uri.unsafeParse(endpointToQuery).addParams(params)
    val request: RequestT[Identity, Either[ResponseException[String, CirceError], R], Any] = basicRequest
      .get(uri)
      .response(asJson[R])

    val response = backend.send(request)

    response.map(_.toRequestResult)
  }
}

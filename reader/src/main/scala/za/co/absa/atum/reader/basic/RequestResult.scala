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

package za.co.absa.atum.reader.basic

import sttp.client3.{DeserializationException, HttpError, Response, ResponseException}
import za.co.absa.atum.model.envelopes.ErrorResponse

object RequestResult {
  type CirceError = io.circe.Error
  type RequestResult[R] = Either[ResponseException[ErrorResponse, CirceError], R]

  implicit class ResponseOps[R](val response: Response[Either[ResponseException[String, CirceError], R]]) extends AnyVal {
    def toRequestResult: RequestResult[R] = {
      response.body.left.map {
        case he: HttpError[String] =>
          ErrorResponse.basedOnStatusCode(he.statusCode.code, he.body) match {
            case Right(er) => HttpError(er, he.statusCode)
            case Left(ce)  => DeserializationException(he.body, ce)
          }
        case de: DeserializationException[CirceError] => de
      }
    }
  }
}

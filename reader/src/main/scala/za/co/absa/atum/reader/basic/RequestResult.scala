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
import sttp.monad.MonadError
import za.co.absa.atum.model.envelopes.ErrorResponse
import za.co.absa.atum.reader.exceptions.RequestException.{CirceError, HttpException, ParsingException}
import za.co.absa.atum.reader.exceptions.{ReaderException, RequestException}
import za.co.absa.atum.reader.result.{GroupedPage, Page}

object RequestResult {
  type RequestResult[R] = Either[RequestException, R]

  def RequestOK[T](value: T): RequestResult[T] = Right(value)
  def RequestFail[T](error: RequestException): RequestResult[T] = Left(error)

  implicit class ResponseOps[R](val response: Response[Either[ResponseException[String, CirceError], R]]) extends AnyVal {
    def toRequestResult: RequestResult[R] = {
      response.body.left.map {
        case he: HttpError[String] =>
          ErrorResponse.basedOnStatusCode(he.statusCode.code, he.body) match {
            case Right(er) => HttpException(he.getMessage, he.statusCode, er, response.request.uri)
            case Left(ce)  => ParsingException.fromCirceError(ce, he.body)
          }
        case de: DeserializationException[CirceError] => ParsingException.fromCirceError(de.error, de.body)
      }
    }
  }

  implicit class RequestPageResultOps[A, F[_]: MonadError](requestResult: RequestResult[Page[A, F]]) {
    def pageMap[B](f: A => B): RequestResult[Page[B, F]] = requestResult.map(_.map(f))
  }

}

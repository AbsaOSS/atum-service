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

package za.co.absa.atum.reader.exceptions

import sttp.model.{StatusCode, Uri}
import za.co.absa.atum.model.envelopes.ErrorResponse

abstract class RequestException(message: String) extends ReaderException(message)


object RequestException {
  type CirceError = io.circe.Error

  final case class HttpException(
                                  message: String,
                                  statusCode: StatusCode,
                                  errorResponse: ErrorResponse,
                                  request: Uri
                                ) extends RequestException(message)

  final case class ParsingException(
                                     message: String,
                                     body: String
                                    ) extends RequestException(message)
  object ParsingException {
    def fromCirceError(error: CirceError, body: String): ParsingException = {
      ParsingException(error.getMessage, body)
    }
  }


  final case class NoDataException(
                                    message: String
                                  ) extends RequestException(message)
}

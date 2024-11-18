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

package za.co.absa.atum.model.envelopes

import io.circe.parser.decode
import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

object ErrorResponse {
  def basedOnStatusCode(statusCode: Int, jsonString: String): Either[Error, ErrorResponse] = {
    statusCode match {
      case 400 => decode[BadRequestResponse](jsonString)
      case 401 => decode[UnauthorizedErrorResponse](jsonString)
      case 404 => decode[NotFoundErrorResponse](jsonString)
      case 409 => decode[ConflictErrorResponse](jsonString)
      case 500 => decode[InternalServerErrorResponse](jsonString)
      case _   => decode[GeneralErrorResponse](jsonString)
    }
  }
}

sealed trait ErrorResponse extends ResponseEnvelope

final case class BadRequestResponse(message: String, requestId: UUID = UUID.randomUUID()) extends ErrorResponse

object BadRequestResponse {
  implicit val decodeBadRequestResponse: Decoder[BadRequestResponse] = deriveDecoder
  implicit val encodeBadRequestResponse: Encoder[BadRequestResponse] = deriveEncoder
}

final case class ConflictErrorResponse(message: String, requestId: UUID = UUID.randomUUID()) extends ErrorResponse

object ConflictErrorResponse {
  implicit val decoderConflictErrorResponse: Decoder[ConflictErrorResponse] = deriveDecoder
  implicit val encoderConflictErrorResponse: Encoder[ConflictErrorResponse] = deriveEncoder
}

final case class NotFoundErrorResponse(message: String, requestId: UUID = UUID.randomUUID()) extends ErrorResponse

object NotFoundErrorResponse {
  implicit val decoderNotFoundErrorResponse: Decoder[NotFoundErrorResponse] = deriveDecoder
  implicit val encoderNotFoundErrorResponse: Encoder[NotFoundErrorResponse] = deriveEncoder
}

final case class GeneralErrorResponse(message: String, requestId: UUID = UUID.randomUUID()) extends ErrorResponse

object GeneralErrorResponse {
  implicit val decodeGeneralErrorResponse: Decoder[GeneralErrorResponse] = deriveDecoder
  implicit val encodeGeneralErrorResponse: Encoder[GeneralErrorResponse] = deriveEncoder
}

final case class InternalServerErrorResponse(message: String, requestId: UUID = UUID.randomUUID()) extends ErrorResponse

object InternalServerErrorResponse {
  implicit val decoderInternalServerErrorResponse: Decoder[InternalServerErrorResponse] = deriveDecoder
  implicit val encoderInternalServerErrorResponse: Encoder[InternalServerErrorResponse] = deriveEncoder
}

final case class ErrorInDataErrorResponse(message: String, requestId: UUID = UUID.randomUUID()) extends ErrorResponse

object ErrorInDataErrorResponse {
  implicit val decoderInternalServerErrorResponse: Decoder[ErrorInDataErrorResponse] = deriveDecoder
  implicit val encoderInternalServerErrorResponse: Encoder[ErrorInDataErrorResponse] = deriveEncoder
}

final case class UnauthorizedErrorResponse(message: String, requestId: UUID = UUID.randomUUID()) extends ErrorResponse

object UnauthorizedErrorResponse {
  implicit val decoderInternalServerErrorResponse: Decoder[UnauthorizedErrorResponse] = deriveDecoder
  implicit val encoderInternalServerErrorResponse: Encoder[UnauthorizedErrorResponse] = deriveEncoder
}


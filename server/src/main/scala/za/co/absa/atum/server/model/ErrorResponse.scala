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

package za.co.absa.atum.server.model

import io.circe._
import io.circe.generic.semiauto._

import java.util.UUID

object ErrorResponse {
  implicit val decodeErrorResponse: Decoder[ErrorResponse] = deriveDecoder
  implicit val encodeErrorResponse: Encoder[ErrorResponse] = deriveEncoder
}

sealed trait ErrorResponse extends ResponseEnvelope {
  def message: String
}

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

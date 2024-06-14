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

//import play.api.libs.json.{Json, Reads, Writes}
//
//sealed trait ErrorResponse {
//  def message: String
//}
//
//object ErrorResponse {
//  implicit val reads: Reads[ErrorResponse] = Json.reads[ErrorResponse]
//  implicit val writes: Writes[ErrorResponse] = Json.writes[ErrorResponse]
//}
//
//final case class BadRequestResponse(message: String) extends ErrorResponse
//
//object BadRequestResponse {
//  implicit val reads: Reads[BadRequestResponse] = Json.reads[BadRequestResponse]
//  implicit val writes: Writes[BadRequestResponse] = Json.writes[BadRequestResponse]
//}
//
//final case class GeneralErrorResponse(message: String) extends ErrorResponse
//
//object GeneralErrorResponse {
//  implicit val reads: Reads[GeneralErrorResponse] = Json.reads[GeneralErrorResponse]
//  implicit val writes: Writes[GeneralErrorResponse] = Json.writes[GeneralErrorResponse]
//}
//
//final case class InternalServerErrorResponse(message: String) extends ErrorResponse
//
//object InternalServerErrorResponse {
//  implicit val reads: Reads[InternalServerErrorResponse] = Json.reads[InternalServerErrorResponse]
//  implicit val writes: Writes[InternalServerErrorResponse] = Json.writes[InternalServerErrorResponse]
//}

import io.circe._, io.circe.generic.semiauto._

sealed trait ErrorResponse {
  def message: String
}

object ErrorResponse {
  implicit val decodeErrorResponse: Decoder[ErrorResponse] = deriveDecoder
  implicit val encodeErrorResponse: Encoder[ErrorResponse] = deriveEncoder
}

final case class BadRequestResponse(message: String) extends ErrorResponse

object BadRequestResponse {
  implicit val decodeBadRequestResponse: Decoder[BadRequestResponse] = deriveDecoder
  implicit val encodeBadRequestResponse: Encoder[BadRequestResponse] = deriveEncoder
}

final case class GeneralErrorResponse(message: String) extends ErrorResponse

object GeneralErrorResponse {
  implicit val decodeGeneralErrorResponse: Decoder[GeneralErrorResponse] = deriveDecoder
  implicit val encodeGeneralErrorResponse: Encoder[GeneralErrorResponse] = deriveEncoder
}

final case class InternalServerErrorResponse(message: String) extends ErrorResponse

object InternalServerErrorResponse {
  implicit val decodeInternalServerErrorResponse: Decoder[InternalServerErrorResponse] = deriveDecoder
  implicit val encodeInternalServerErrorResponse: Encoder[InternalServerErrorResponse] = deriveEncoder
}

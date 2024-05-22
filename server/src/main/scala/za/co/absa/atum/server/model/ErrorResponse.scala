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

import play.api.libs.json.{Json, Reads, Writes}

object ErrorResponse {

  sealed trait ErrorResponse {
    def message: String
  }

  implicit val reads: Reads[ErrorResponse] = Json.reads[ErrorResponse]
  implicit val writes: Writes[ErrorResponse] = Json.writes[ErrorResponse]

  final case class BadRequestResponse(message: String) extends ErrorResponse

  implicit val readsBadRequestResponse: Reads[BadRequestResponse] = Json.reads[BadRequestResponse]
  implicit val writesBadRequestResponse: Writes[BadRequestResponse] = Json.writes[BadRequestResponse]

  final case class GeneralErrorResponse(message: String) extends ErrorResponse

  implicit val readsGeneralErrorResponse: Reads[GeneralErrorResponse] = Json.reads[GeneralErrorResponse]
  implicit val writesGeneralErrorResponse: Writes[GeneralErrorResponse] = Json.writes[GeneralErrorResponse]

  final case class InternalServerErrorResponse(message: String) extends ErrorResponse

  implicit val readsInternalServerErrorResponse: Reads[InternalServerErrorResponse] = Json.reads[InternalServerErrorResponse]
  implicit val writesInternalServerErrorResponse: Writes[InternalServerErrorResponse] = Json.writes[InternalServerErrorResponse]

}

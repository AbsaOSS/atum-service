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

package za.co.absa.atum.server.api.http

import sttp.model.StatusCode
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import za.co.absa.atum.server.model.{BadRequestResponse, ErrorResponse, GeneralErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse}
import sttp.tapir.typelevel.MatchType
import sttp.tapir.ztapir._
import sttp.tapir.{EndpointOutput, PublicEndpoint}
import za.co.absa.atum.server.Constants.Endpoints.{Api, V1, V2}

import java.util.UUID

trait BaseEndpoints {

  implicit val uuidMatchType: MatchType[UUID] = (a: Any) => a.isInstanceOf[UUID]

  private val badRequestOneOfVariant: EndpointOutput.OneOfVariant[BadRequestResponse] = {
    oneOfVariantFromMatchType(
      StatusCode.BadRequest,
      jsonBody[BadRequestResponse]
    )
  }

  private val internalServerErrorOneOfVariant: EndpointOutput.OneOfVariant[InternalServerErrorResponse] = {
    oneOfVariantFromMatchType(
      StatusCode.InternalServerError,
      jsonBody[InternalServerErrorResponse]
    )
  }

  private val generalErrorOneOfVariant: EndpointOutput.OneOfVariant[GeneralErrorResponse] = {
    oneOfVariantFromMatchType(
      StatusCode.BadRequest,
      jsonBody[GeneralErrorResponse]
    )
  }

  private val baseEndpoint: PublicEndpoint[Unit, ErrorResponse, Unit, Any] = {
    endpoint.errorOut(
      oneOf[ErrorResponse](
        badRequestOneOfVariant,
        generalErrorOneOfVariant,
        internalServerErrorOneOfVariant
      )
    )
  }

  protected val apiV1: PublicEndpoint[Unit, ErrorResponse, Unit, Any] = {
    baseEndpoint.in(Api / V1)
  }

  protected val apiV2: PublicEndpoint[Unit, ErrorResponse, Unit, Any] = {
    baseEndpoint.in(Api / V2)
  }

  def pathToAPIv1CompatibleFormat(apiURLPath: String): String = {
    // this is basically kebab-case/snake_case to camelCase
    val inputParts = apiURLPath.split("[_-]")

    // Capitalize the first letter of each part except the first one (lowercase always)
    inputParts.head.toLowerCase + inputParts.tail.map(_.capitalize).mkString("")
  }

  protected val notFoundErrorOneOfVariant: EndpointOutput.OneOfVariant[NotFoundErrorResponse] = {
    oneOfVariantFromMatchType(
      StatusCode.NotFound,
      jsonBody[NotFoundErrorResponse]
    )
  }

}

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

package za.co.absa.atum.server

import sttp.model.StatusCode
import sttp.tapir.json.play.jsonBody
import sttp.tapir.{Endpoint, EndpointOutput, PublicEndpoint, oneOfVariantFromMatchType}
import sttp.tapir.ztapir._
import sttp.tapir.generic.auto.schemaForCaseClass
import za.co.absa.atum.server.Constants.{Api, V1}
import za.co.absa.atum.server.model._

trait BaseEndpoints {

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

  protected val apiV1: Endpoint[Unit, Unit, ErrorResponse, Unit, Any] = {
    baseEndpoint.in(Api / V1)
  }

}

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

package za.co.absa.atum.server.api.common.http

import sttp.model.StatusCode
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.typelevel.MatchType
import sttp.tapir.ztapir._
import sttp.tapir.{EndpointOutput, PublicEndpoint}
import za.co.absa.atum.model.ApiPaths._
import za.co.absa.atum.model.envelopes._
import zio.ZIO

import java.util.UUID

trait BaseEndpoints {

  implicit val uuidMatchType: MatchType[UUID] = (a: Any) => a.isInstanceOf[UUID]

  protected val conflictErrorOneOfVariant: EndpointOutput.OneOfVariant[ConflictErrorResponse] = {
    oneOfVariantFromMatchType(
      StatusCode.Conflict,
      jsonBody[ConflictErrorResponse]
    )
  }

  protected val notFoundErrorOneOfVariant: EndpointOutput.OneOfVariant[NotFoundErrorResponse] = {
    oneOfVariantFromMatchType(
      StatusCode.NotFound,
      jsonBody[NotFoundErrorResponse]
    )
  }

  protected val errorInDataOneOfVariant: EndpointOutput.OneOfVariant[ErrorInDataErrorResponse] = {
    oneOfVariantFromMatchType(
      StatusCode.Conflict,
      jsonBody[ErrorInDataErrorResponse]
    )
  }

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

  protected def createServerEndpoint[I, E, O](
    endpoint: PublicEndpoint[I, E, O, Any],
    logic: I => ZIO[HttpEnv.Env, E, O]
  ): ZServerEndpoint[HttpEnv.Env, Any] = {
    endpoint.zServerLogic(logic).widen[HttpEnv.Env]
  }

}

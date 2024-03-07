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

import sttp.monad.MonadError
import sttp.tapir.DecodeResult
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.play.jsonBody
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.respond
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.ztapir.{headers, statusCode}
import za.co.absa.atum.server.model.BadRequestResponse
import zio.interop.catz._

trait ServerOptions {

  protected def http4sServerOptions(
    prometheusMetricsInterceptor: Option[MetricsRequestInterceptor[HttpEnv.F]]
  ): Http4sServerOptions[HttpEnv.F] = Http4sServerOptions
    .customiseInterceptors[HttpEnv.F]
    .decodeFailureHandler(decodeFailureHandler)
    .metricsInterceptor(prometheusMetricsInterceptor)
    .options

  private val decodeFailureHandler: DecodeFailureHandler[HttpEnv.F] = new DecodeFailureHandler[HttpEnv.F] {
    override def apply(
      ctx: DecodeFailureContext
    )(implicit monad: MonadError[HttpEnv.F]): HttpEnv.F[Option[ValuedEndpointOutput[_]]] = {
      monad.unit(
        respond(ctx).map { case (sc, hs) =>
          val message = ctx.failure match {
            case DecodeResult.Missing => s"Decoding error - missing value."
            case DecodeResult.Multiple(vs) => s"Decoding error - $vs."
            case DecodeResult.Error(original, _) => s"Decoding error for an input value '$original'."
            case DecodeResult.Mismatch(_, actual) => s"Unexpected value '$actual'."
            case DecodeResult.InvalidValue(errors) => s"Validation error - $errors."
          }
          val errorResponse = BadRequestResponse(message)
          ValuedEndpointOutput(statusCode.and(headers).and(jsonBody[BadRequestResponse]), (sc, hs, errorResponse))
        }
      )
    }
  }

}

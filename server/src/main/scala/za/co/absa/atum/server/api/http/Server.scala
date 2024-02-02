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

import cats.syntax.semigroupk._
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.monad.MonadError
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.play.jsonBody
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.respond
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir._
import sttp.tapir.{DecodeResult, Endpoint, PublicEndpoint, headers, statusCode}
import za.co.absa.atum.server.Constants.{SwaggerApiName, SwaggerApiVersion}
import za.co.absa.atum.server.api.controller._
import za.co.absa.atum.server.model.BadRequestResponse
import zio.interop.catz._
import zio.{RIO, ZIO}

trait Server extends Endpoints {

  type Env = PartitioningController with CheckpointController
  type F[A] = RIO[Env, A]

  private val decodeFailureHandler: DecodeFailureHandler[F] = new DecodeFailureHandler[F] {
    override def apply(ctx: DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[ValuedEndpointOutput[_]]] = {
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

  private val http4sServerOptions: Http4sServerOptions[F] = Http4sServerOptions
    .customiseInterceptors[F]
    .decodeFailureHandler(decodeFailureHandler)
    .options

  private def createServerEndpoint[I, E, O](
    endpoint: PublicEndpoint[I, E, O, Any],
    logic: I => ZIO[Env, E, O]
  ): ZServerEndpoint[Env, Any] = {
    endpoint.zServerLogic(logic).widen[Env]
  }

  private def createAllServerRoutes: HttpRoutes[F] = {
    val endpoints = List(
      createServerEndpoint(createCheckpointEndpoint, CheckpointController.createCheckpoint),
      createServerEndpoint(createPartitioningEndpoint, PartitioningController.createPartitioningIfNotExists),
      createServerEndpoint(createOrUpdateAdditionalDataEndpoint, PartitioningController.createOrUpdateAdditionalData)
    )
    ZHttp4sServerInterpreter[Env](http4sServerOptions).from(endpoints).toRoutes
  }

  private def createSwaggerRoutes: HttpRoutes[F] = {
    val endpoints = List(createCheckpointEndpoint, createPartitioningEndpoint, createOrUpdateAdditionalDataEndpoint)
    ZHttp4sServerInterpreter[Env](http4sServerOptions)
      .from(SwaggerInterpreter().fromEndpoints[F](endpoints, SwaggerApiName, SwaggerApiVersion))
      .toRoutes
  }

  protected val server: ZIO[Env, Throwable, Unit] =
    ZIO.executor.flatMap { executor =>
      BlazeServerBuilder[F]
        .withExecutionContext(executor.asExecutionContext)
        .withHttpApp(Router("/" -> (createAllServerRoutes <+> createSwaggerRoutes)).orNotFound)
        .serve
        .compile
        .drain
    }

}

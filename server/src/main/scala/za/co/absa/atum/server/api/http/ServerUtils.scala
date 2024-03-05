package za.co.absa.atum.server.api.http

import sttp.monad.MonadError
import sttp.tapir.{DecodeResult, PublicEndpoint}
import sttp.tapir.ztapir._
import sttp.tapir.json.play.jsonBody
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.respond
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.ztapir.{RichZEndpoint, ZServerEndpoint, headers, statusCode}
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import za.co.absa.atum.server.model.BadRequestResponse
import zio._
import zio.interop.catz._

trait ServerUtils extends HttpEnv {

  protected def http4sServerOptions(
    prometheusMetricsInterceptor: Option[MetricsRequestInterceptor[F]]
  ): Http4sServerOptions[F] = Http4sServerOptions
    .customiseInterceptors[F]
    .decodeFailureHandler(decodeFailureHandler)
    .metricsInterceptor(prometheusMetricsInterceptor)
    .options

  protected def createServerEndpoint[I, E, O](
    endpoint: PublicEndpoint[I, E, O, Any],
    logic: I => ZIO[Env, E, O]
  ): ZServerEndpoint[Env, Any] = {
    endpoint.zServerLogic(logic).widen[Env]
  }

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

}

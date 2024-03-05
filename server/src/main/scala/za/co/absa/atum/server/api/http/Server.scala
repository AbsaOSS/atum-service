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
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.respond
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir._
import sttp.tapir.{DecodeResult, PublicEndpoint, headers, statusCode}
import za.co.absa.atum.server.Constants.{SwaggerApiName, SwaggerApiVersion}
import za.co.absa.atum.server.api.controller._
import za.co.absa.atum.server.config.SslConfig
import za.co.absa.atum.server.model.BadRequestResponse
import zio._
import zio.interop.catz._
import zio.metrics.connectors.prometheus.PrometheusPublisher

import javax.net.ssl.SSLContext

trait Server extends ServerUtils with Endpoints with HttpEnv {

  private def createAllServerRoutes: HttpRoutes[F] = {
    val endpoints = List(
      createServerEndpoint(createCheckpointEndpoint, CheckpointController.createCheckpoint),
      createServerEndpoint(createPartitioningEndpoint, PartitioningController.createPartitioningIfNotExists)
    )
    ZHttp4sServerInterpreter[Env](http4sServerOptions(Metrics.prometheusMetrics.metricsInterceptor()).from(endpoints).toRoutes
  }

  private def createSwaggerRoutes: HttpRoutes[F] = {
    val endpoints = List(createCheckpointEndpoint, createPartitioningEndpoint)
    ZHttp4sServerInterpreter[Env](http4sServerOptions())
      .from(SwaggerInterpreter().fromEndpoints[F](endpoints, SwaggerApiName, SwaggerApiVersion))
      .toRoutes
  }


  private def zioMetricsRoutes: HttpRoutes[F] = {
    ZHttp4sServerInterpreter[Env].from(
      List(createServerEndpoint(zioMetricsEndpoint, (_: Unit) => ZIO.serviceWithZIO[PrometheusPublisher](_.get)))
    ).toRoutes
  }

  // 3. Expose metrics endpoint which will can be scraped by Prometheus
  private val metricsRoutes = Http4sServerInterpreter[F]().toRoutes(Metrics.prometheusMetrics.metricsEndpoint) // route at path /metrics
  private val allRoutes = createAllServerRoutes <+> createSwaggerRoutes <+> metricsRoutes <+> zioMetricsRoutes

  private def createServer(port: Int, sslContext: Option[SSLContext] = None): ZIO[Env, Throwable, Unit] =
    ZIO.executor.flatMap { executor =>
      val builder = BlazeServerBuilder[F]
        .bindHttp(port, "0.0.0.0")
        .withExecutionContext(executor.asExecutionContext)
        .withHttpApp(Router("/" -> allRoutes).orNotFound)

      val builderWithSsl = sslContext.fold(builder)(ctx => builder.withSslContext(ctx))
      builderWithSsl.serve.compile.drain
    }

  private val httpServer: ZIO[Env, Throwable, Unit] = createServer(8080)
  private val httpsServer: ZIO[Env, Throwable, Unit] = SSL.context.flatMap { context =>
    createServer(8443, Some(context))
  }

  protected val server: ZIO[Env, Throwable, Unit] = for {
    sslConfig <- ZIO.config[SslConfig](SslConfig.config)
    server <- if (sslConfig.enabled) httpsServer else httpServer
  } yield server

}

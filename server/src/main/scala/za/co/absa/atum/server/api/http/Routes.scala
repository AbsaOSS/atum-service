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
import sttp.tapir.PublicEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir._
import za.co.absa.atum.server.Constants.{SwaggerApiName, SwaggerApiVersion}
import za.co.absa.atum.server.api.controller.{CheckpointController, PartitioningController}
import za.co.absa.atum.server.config.{HttpMonitoringConfig, JvmMonitoringConfig}
import zio._
import zio.interop.catz._
import zio.metrics.connectors.prometheus.PrometheusPublisher

trait Routes extends Endpoints with ServerOptions {

  private def createAllServerRoutes(httpMonitoringConfig: HttpMonitoringConfig): HttpRoutes[HttpEnv.F] = {
    val metricsInterceptorOption: Option[MetricsRequestInterceptor[HttpEnv.F]] = {
      if (httpMonitoringConfig.enabled) Some(HttpMetrics.prometheusMetrics.metricsInterceptor()) else None
    }
    val endpoints = List(
      createServerEndpoint(createCheckpointEndpoint, CheckpointController.createCheckpoint),
      createServerEndpoint(createPartitioningEndpoint, PartitioningController.createPartitioningIfNotExists),
      createServerEndpoint(createOrUpdateAdditionalDataEndpoint, PartitioningController.createOrUpdateAdditionalData),
      createServerEndpoint(healthEndpoint, (_: Unit) => ZIO.unit),
    )
    ZHttp4sServerInterpreter[HttpEnv.Env](http4sServerOptions(metricsInterceptorOption)).from(endpoints).toRoutes
  }

  private val http4sMetricsRoutes =
    Http4sServerInterpreter[HttpEnv.F]().toRoutes(HttpMetrics.prometheusMetrics.metricsEndpoint)

  private def createSwaggerRoutes: HttpRoutes[HttpEnv.F] = {
    val endpoints = List(createCheckpointEndpoint, createPartitioningEndpoint, createOrUpdateAdditionalDataEndpoint)
    ZHttp4sServerInterpreter[HttpEnv.Env](http4sServerOptions(None))
      .from(SwaggerInterpreter().fromEndpoints[HttpEnv.F](endpoints, SwaggerApiName, SwaggerApiVersion))
      .toRoutes
  }

  private def zioMetricsRoutes(jvmMonitoringConfig: JvmMonitoringConfig): HttpRoutes[HttpEnv.F] = {
    val endpointsList = if (jvmMonitoringConfig.enabled) {
      List(createServerEndpoint(zioMetricsEndpoint, (_: Unit) => ZIO.serviceWithZIO[PrometheusPublisher](_.get)))
    } else Nil
    ZHttp4sServerInterpreter[HttpEnv.Env]().from(endpointsList).toRoutes
  }

  private def createServerEndpoint[I, E, O](
    endpoint: PublicEndpoint[I, E, O, Any],
    logic: I => ZIO[HttpEnv.Env, E, O]
  ): ZServerEndpoint[HttpEnv.Env, Any] = {
    endpoint.zServerLogic(logic).widen[HttpEnv.Env]
  }

  protected def allRoutes(
    httpMonitoringConfig: HttpMonitoringConfig,
    jvmMonitoringConfig: JvmMonitoringConfig
  ): HttpRoutes[HttpEnv.F] = {
    createAllServerRoutes(httpMonitoringConfig) <+>
      createSwaggerRoutes <+>
      (if (httpMonitoringConfig.enabled) http4sMetricsRoutes else HttpRoutes.empty[HttpEnv.F]) <+>
      zioMetricsRoutes(jvmMonitoringConfig)
  }

}

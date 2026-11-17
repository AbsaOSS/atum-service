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

import cats.syntax.semigroupk._
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.AnyEndpoint
import sttp.tapir.ztapir._
import za.co.absa.atum.server.config.{HikariMonitoringConfig, HttpMonitoringConfig, JvmMonitoringConfig}
import zio._
import zio.interop.catz._
import zio.metrics.connectors.prometheus.PrometheusPublisher

import za.co.absa.atum.server.api

object Routes extends ServerOptions with ServerUtils {

  private def createAllServerRoutes(httpMonitoringConfig: HttpMonitoringConfig): HttpRoutes[HttpEnv.F] = {
    val metricsInterceptorOption: Option[MetricsRequestInterceptor[HttpEnv.F]] = {
      if (httpMonitoringConfig.enabled) Some(HttpMetrics.prometheusMetrics.metricsInterceptor()) else None
    }
    ZHttp4sServerInterpreter[HttpEnv.Env](http4sServerOptions(metricsInterceptorOption))
      .from(servedEndpoints)
      .toRoutes
  }

  /** All endpoints served by the API (v1 + v2 + common), excluding the metrics endpoints. */
  private val servedEndpoints: List[ZServerEndpoint[HttpEnv.Env, Any]] =
    api.v1.http.Endpoints.serverEndpoints ++
      api.v2.http.Endpoints.serverEndpoints ++
      api.common.http.Endpoints.serverEndpoints

  /**
   *  All endpoints documented by the Swagger UI / OpenAPI (also used by tests). Derived from the served endpoints,
   *  so the documentation cannot drift from what is actually served.
   */
  val swaggerEndpoints: List[AnyEndpoint] = servedEndpoints.map(_.endpoint)

  private def createSwaggerRoutes: HttpRoutes[HttpEnv.F] = {
    ZHttp4sServerInterpreter[HttpEnv.Env](http4sServerOptions(None))
      .from(SwaggerInterpreter().fromEndpoints[HttpEnv.F](swaggerEndpoints, "Atum API", "1.0"))
      .toRoutes
  }

  private def http4sMetricsRoutes(httpMonitoringConfig: HttpMonitoringConfig): HttpRoutes[HttpEnv.F] = {
    if (httpMonitoringConfig.enabled) {
      Http4sServerInterpreter[HttpEnv.F]().toRoutes(HttpMetrics.prometheusMetrics.metricsEndpoint)
    } else HttpRoutes.empty[HttpEnv.F]
  }

  private def zioMetricsRoutes(jvmMonitoringConfig: JvmMonitoringConfig): HttpRoutes[HttpEnv.F] = {
    val endpointsList = if (jvmMonitoringConfig.enabled) {
      List(
        createServerEndpoint(
          api.common.http.Endpoints.zioMetricsEndpoint,
          (_: Unit) => ZIO.serviceWithZIO[PrometheusPublisher](_.get)
        )
      )
    } else Nil
    ZHttp4sServerInterpreter[HttpEnv.Env]().from(endpointsList).toRoutes
  }

  private def hikariMetricsRoutes(hikariMonitoringConfig: HikariMonitoringConfig): HttpRoutes[HttpEnv.F] = {
    val endpointsList = if (hikariMonitoringConfig.enabled) {
      List(
        createServerEndpoint(
          api.common.http.Endpoints.hikariMetricsEndpoint,
          api.common.http.Endpoints.hikariMetricsLogic
        )
      )
    } else Nil
    ZHttp4sServerInterpreter[HttpEnv.Env]().from(endpointsList).toRoutes
  }

  def allRoutes(
    httpMonitoringConfig: HttpMonitoringConfig,
    hikariMonitoringConfig: HikariMonitoringConfig,
    jvmMonitoringConfig: JvmMonitoringConfig
  ): HttpRoutes[HttpEnv.F] = {
    createAllServerRoutes(httpMonitoringConfig) <+>
      createSwaggerRoutes <+>
      http4sMetricsRoutes(httpMonitoringConfig) <+>
      hikariMetricsRoutes(hikariMonitoringConfig) <+>
      zioMetricsRoutes(jvmMonitoringConfig)
  }

}

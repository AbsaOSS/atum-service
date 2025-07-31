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
import sttp.tapir.ztapir._
import za.co.absa.atum.server.config.{HttpMonitoringConfig, JvmMonitoringConfig}
import zio._
import zio.interop.catz._
import zio.metrics.connectors.prometheus.PrometheusPublisher

import za.co.absa.atum.server.api

object Routes extends ServerOptions with ServerUtils {

  private def createAllServerRoutes(httpMonitoringConfig: HttpMonitoringConfig): HttpRoutes[HttpEnv.F] = {
    val metricsInterceptorOption: Option[MetricsRequestInterceptor[HttpEnv.F]] = {
      if (httpMonitoringConfig.enabled) Some(HttpMetrics.prometheusMetrics.metricsInterceptor()) else None
    }
    val endpoints: List[ZServerEndpoint[HttpEnv.Env, Any]] = {
      api.v1.http.Endpoints.serverEndpoints ++
        api.v2.http.Endpoints.serverEndpoints ++
        api.common.http.Endpoints.serverEndpoints
    }

    ZHttp4sServerInterpreter[HttpEnv.Env](http4sServerOptions(metricsInterceptorOption)).from(endpoints).toRoutes
  }

  private val http4sMetricsRoutes =
    Http4sServerInterpreter[HttpEnv.F]().toRoutes(HttpMetrics.prometheusMetrics.metricsEndpoint)

  private def createSwaggerRoutes: HttpRoutes[HttpEnv.F] = {
    val endpoints = List(
      api.v1.http.Endpoints.createCheckpointEndpoint,
      api.v1.http.Endpoints.createPartitioningEndpoint,
      api.v2.http.Endpoints.postCheckpointEndpoint,
      api.v2.http.Endpoints.postPartitioningEndpoint,
      api.v2.http.Endpoints.getPartitioningAdditionalDataEndpoint,
      api.v2.http.Endpoints.patchPartitioningAdditionalDataEndpoint,
      api.v2.http.Endpoints.patchPartitioningParentEndpoint,
      api.v2.http.Endpoints.getPartitioningCheckpointsEndpoint,
      api.v2.http.Endpoints.getPartitioningCheckpointEndpoint,
      api.v2.http.Endpoints.getPartitioningEndpoint,
      api.v2.http.Endpoints.getPartitioningMeasuresEndpoint,
      api.v2.http.Endpoints.getPartitioningMainFlowEndpoint,
      api.v2.http.Endpoints.getFlowPartitioningsEndpoint,
      api.v2.http.Endpoints.getFlowCheckpointsEndpoint,
      api.v2.http.Endpoints.getPartitioningAncestorsEndpoint,
      api.common.http.Endpoints.healthEndpoint
    )
    ZHttp4sServerInterpreter[HttpEnv.Env](http4sServerOptions(None))
      .from(SwaggerInterpreter().fromEndpoints[HttpEnv.F](endpoints, "Atum API", "1.0"))
      .toRoutes
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

  def allRoutes(
    httpMonitoringConfig: HttpMonitoringConfig,
    jvmMonitoringConfig: JvmMonitoringConfig
  ): HttpRoutes[HttpEnv.F] = {
    createAllServerRoutes(httpMonitoringConfig) <+>
      createSwaggerRoutes <+>
      (if (httpMonitoringConfig.enabled) http4sMetricsRoutes else HttpRoutes.empty[HttpEnv.F]) <+>
      zioMetricsRoutes(jvmMonitoringConfig)
  }

}

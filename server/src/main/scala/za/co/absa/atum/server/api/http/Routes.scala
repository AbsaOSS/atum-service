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
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.ztapir._
import za.co.absa.atum.model.dto.{CheckpointDTO, CheckpointV2DTO}
import za.co.absa.atum.server.Constants.{SwaggerApiName, SwaggerApiVersion}
import za.co.absa.atum.server.api.controller.{CheckpointController, FlowController, PartitioningController}
import za.co.absa.atum.server.api.http.ApiPaths.V2Paths
import za.co.absa.atum.server.config.{HttpMonitoringConfig, JvmMonitoringConfig}
import za.co.absa.atum.server.model.ErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio._
import zio.interop.catz._
import zio.metrics.connectors.prometheus.PrometheusPublisher

trait Routes extends Endpoints with ServerOptions {

  private def createAllServerRoutes(httpMonitoringConfig: HttpMonitoringConfig): HttpRoutes[HttpEnv.F] = {
    val metricsInterceptorOption: Option[MetricsRequestInterceptor[HttpEnv.F]] = {
      if (httpMonitoringConfig.enabled) Some(HttpMetrics.prometheusMetrics.metricsInterceptor()) else None
    }
    val endpoints = List(
      createServerEndpoint(createCheckpointEndpointV1, CheckpointController.createCheckpointV1),
      createServerEndpoint[
        (Long, CheckpointV2DTO),
        ErrorResponse,
        (SingleSuccessResponse[CheckpointV2DTO], String)
      ](
        postCheckpointEndpointV2,
        { case (partitioningId: Long, checkpointV2DTO: CheckpointV2DTO) =>
          CheckpointController.postCheckpointV2(partitioningId, checkpointV2DTO)
        }
      ),
      createServerEndpoint(createPartitioningEndpointV1, PartitioningController.createPartitioningIfNotExistsV1),
      createServerEndpoint(createPartitioningEndpointV2, PartitioningController.createPartitioningIfNotExistsV2),
      createServerEndpoint(
        createOrUpdateAdditionalDataEndpointV2,
        PartitioningController.createOrUpdateAdditionalDataV2
      ),
      createServerEndpoint[
        (Long, String),
        ErrorResponse,
        SingleSuccessResponse[CheckpointV2DTO]
      ](
        getPartitioningCheckpointEndpointV2,
        { case (partitioningId: Long, checkpointId: String) =>
          CheckpointController.getPartitioningCheckpointV2(partitioningId, checkpointId)
        }
      ),
      createServerEndpoint(getPartitioningCheckpointsEndpointV2, PartitioningController.getPartitioningCheckpointsV2),
      createServerEndpoint(getFlowCheckpointsEndpointV2, FlowController.getFlowCheckpointsV2),
      createServerEndpoint(healthEndpoint, (_: Unit) => ZIO.unit)
    )
    ZHttp4sServerInterpreter[HttpEnv.Env](http4sServerOptions(metricsInterceptorOption)).from(endpoints).toRoutes
  }

  private val http4sMetricsRoutes =
    Http4sServerInterpreter[HttpEnv.F]().toRoutes(HttpMetrics.prometheusMetrics.metricsEndpoint)

  private def createSwaggerRoutes: HttpRoutes[HttpEnv.F] = {
    val endpoints = List(
      createCheckpointEndpointV1,
      postCheckpointEndpointV2,
      createPartitioningEndpointV1,
      createPartitioningEndpointV2,
      createOrUpdateAdditionalDataEndpointV2,
      getPartitioningCheckpointsEndpointV2,
      getFlowCheckpointsEndpointV2
    )
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

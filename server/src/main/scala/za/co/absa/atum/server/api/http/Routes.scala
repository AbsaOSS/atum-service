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
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataPatchDTO, CheckpointV2DTO, PartitioningWithIdDTO}
import za.co.absa.atum.server.api.controller.{CheckpointController, FlowController, PartitioningController}
import za.co.absa.atum.server.config.{HttpMonitoringConfig, JvmMonitoringConfig}
import za.co.absa.atum.server.model.{ErrorResponse, StatusResponse}
import za.co.absa.atum.server.model.SuccessResponse._
import zio._
import zio.interop.catz._
import zio.metrics.connectors.prometheus.PrometheusPublisher

import java.util.UUID

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
      createServerEndpoint(postPartitioningEndpointV2, PartitioningController.postPartitioning),
      createServerEndpoint(
        getPartitioningAdditionalDataEndpointV2,
        PartitioningController.getPartitioningAdditionalDataV2
      ),
      createServerEndpoint[
        (Long, AdditionalDataPatchDTO),
        ErrorResponse,
        SingleSuccessResponse[AdditionalDataDTO]
      ](
        patchPartitioningAdditionalDataEndpointV2,
        { case (partitioningId: Long, additionalDataPatchDTO: AdditionalDataPatchDTO) =>
          PartitioningController.patchPartitioningAdditionalDataV2(partitioningId, additionalDataPatchDTO)
        }
      ),
      createServerEndpoint(getPartitioningEndpointV2, PartitioningController.getPartitioning),
      createServerEndpoint[
        (Long, UUID),
        ErrorResponse,
        SingleSuccessResponse[CheckpointV2DTO]
      ](
        getPartitioningCheckpointEndpointV2,
        { case (partitioningId: Long, checkpointId: UUID) =>
          CheckpointController.getPartitioningCheckpointV2(partitioningId, checkpointId)
        }
      ),
      createServerEndpoint[
        (Long, Option[Int], Option[Long], Option[String]),
        ErrorResponse,
        PaginatedResponse[CheckpointV2DTO]
      ](
        getPartitioningCheckpointsEndpointV2,
        { case (partitioningId: Long, limit: Option[Int], offset: Option[Long], checkpointName: Option[String]) =>
          CheckpointController.getPartitioningCheckpoints(partitioningId, limit, offset, checkpointName)
        }
      ),
      createServerEndpoint(getPartitioningByIdEndpointV2, PartitioningController.getPartitioningByIdV2),
      createServerEndpoint(getPartitioningMeasuresEndpointV2, PartitioningController.getPartitioningMeasuresV2),
      createServerEndpoint(getPartitioningMainFlowEndpointV2, PartitioningController.getPartitioningMainFlow),
      createServerEndpoint[
        (Long, Option[Int], Option[Long]),
        ErrorResponse,
        PaginatedResponse[PartitioningWithIdDTO]
      ](
        getFlowPartitioningsEndpointV2,
        { case (flowId: Long, limit: Option[Int], offset: Option[Long]) =>
          PartitioningController.getFlowPartitionings(flowId, limit, offset)
        }
      ),
      createServerEndpoint(healthEndpoint, (_: Unit) => ZIO.succeed(StatusResponse.up))
    )
    ZHttp4sServerInterpreter[HttpEnv.Env](http4sServerOptions(metricsInterceptorOption)).from(endpoints).toRoutes
  }

  private val http4sMetricsRoutes =
    Http4sServerInterpreter[HttpEnv.F]().toRoutes(HttpMetrics.prometheusMetrics.metricsEndpoint)

  private def createSwaggerRoutes: HttpRoutes[HttpEnv.F] = {
    val endpoints = List(
      createCheckpointEndpointV1,
      //      postCheckpointEndpointV2,
      createPartitioningEndpointV1,
      //      postPartitioningEndpointV2,
      //      patchPartitioningAdditionalDataEndpointV2,
      //      getPartitioningCheckpointsEndpointV2,
      //      getPartitioningCheckpointEndpointV2,
      //      getPartitioningMeasuresEndpointV2,
      //      getPartitioningEndpointV2,
      //      getPartitioningMeasuresEndpointV2,
      //      getFlowPartitioningsEndpointV2,
      //      getPartitioningMainFlowEndpointV2
      healthEndpoint
    )
    ZHttp4sServerInterpreter[HttpEnv.Env](http4sServerOptions(None))
      .from(SwaggerInterpreter().fromEndpoints[HttpEnv.F](endpoints, "Atum API", "1.0"))
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

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

package za.co.absa.atum.server

import za.co.absa.atum.server.api.controller._
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings
import za.co.absa.atum.server.api.database.{PostgresDatabaseProvider, TransactorProvider}
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.http.Server
import za.co.absa.atum.server.api.repository._
import za.co.absa.atum.server.api.service._
import za.co.absa.atum.server.aws.AwsSecretsProviderImpl
import za.co.absa.atum.server.config.JvmMonitoringConfig
import zio._
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.backend.SLF4J
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics

import java.time.Duration

object Main extends ZIOAppDefault with Server {

  private val configProvider: ConfigProvider = TypesafeConfigProvider.fromResourcePath()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    ZIO.config[JvmMonitoringConfig](JvmMonitoringConfig.config).flatMap { jvmMonitoringConfig =>
      server
        .provide(
          PartitioningControllerImpl.layer,
          CheckpointControllerImpl.layer,
          FlowControllerImpl.layer,
          PartitioningServiceImpl.layer,
          CheckpointServiceImpl.layer,
          FlowServiceImpl.layer,
          PartitioningRepositoryImpl.layer,
          CheckpointRepositoryImpl.layer,
          FlowRepositoryImpl.layer,
          CreatePartitioningIfNotExists.layer,
          CreatePartitioning.layer,
          GetPartitioningMeasures.layer,
          GetPartitioningMeasuresById.layer,
          GetPartitioningAdditionalData.layer,
          CreateOrUpdateAdditionalData.layer,
          GetPartitioningCheckpoints.layer,
          WriteCheckpoint.layer,
          WriteCheckpointV2.layer,
          GetPartitioningCheckpointV2.layer,
          GetFlowCheckpoints.layer,
          GetPartitioningById.layer,
          GetPartitioning.layer,
          GetFlowPartitionings.layer,
          GetPartitioningMainFlow.layer,
          PostgresDatabaseProvider.layer,
          TransactorProvider.layer,
          AwsSecretsProviderImpl.layer,
          zio.Scope.default,
          // for Prometheus
          prometheus.publisherLayer,
          prometheus.prometheusLayer,
          // enabling conditionally collection of ZIO runtime metrics and default JVM metrics
          if (jvmMonitoringConfig.enabled) {
            ZLayer.succeed(MetricsConfig(Duration.ofSeconds(jvmMonitoringConfig.intervalInSeconds))) ++
              Runtime.enableRuntimeMetrics.unit ++ DefaultJvmMetrics.live.unit
          } else {
            ZLayer.succeed(MetricsConfig(Duration.ofSeconds(Long.MaxValue)))
          }
        )
    }

  }

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j >>> Runtime.setConfigProvider(configProvider)

}

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

import za.co.absa.atum.server.api.common.http.Server

import za.co.absa.atum.server.api.v1.controller.{
  PartitioningControllerImpl => PartitioningControllerImplV1,
  CheckpointControllerImpl => CheckpointControllerImplV1
}
import za.co.absa.atum.server.api.v2.controller.{
  PartitioningControllerImpl => PartitioningControllerImplV2,
  CheckpointControllerImpl => CheckpointControllerImplV2,
  FlowControllerImpl => FlowControllerImplV2
}

import za.co.absa.atum.server.api.v1.service.{
  PartitioningServiceImpl => PartitioningServiceImplV1,
  CheckpointServiceImpl => CheckpointServiceImplV1
}

import za.co.absa.atum.server.api.v2.service.{
  PartitioningServiceImpl => PartitioningServiceImplV2,
  CheckpointServiceImpl => CheckpointServiceImplV2,
  FlowServiceImpl => FlowServiceImplV2
}

import za.co.absa.atum.server.api.v1.repository.{
  PartitioningRepositoryImpl => PartitioningRepositoryImplV1,
  CheckpointRepositoryImpl => CheckpointRepositoryImplV1
}

import za.co.absa.atum.server.api.v2.repository.{
  PartitioningRepositoryImpl => PartitioningRepositoryImplV2,
  CheckpointRepositoryImpl => CheckpointRepositoryImplV2,
  FlowRepositoryImpl => FlowRepositoryImplV2
}

import za.co.absa.atum.server.api.database.flows.functions.{GetFlowCheckpoints, GetFlowPartitionings}
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.database.{PostgresDatabaseProvider, TransactorProvider}

import za.co.absa.atum.server.aws.AwsSecretsProviderImpl
import za.co.absa.atum.server.config.JvmMonitoringConfig
import zio._
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.backend.SLF4J
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics

import java.time.Duration

object Main extends ZIOAppDefault {

  private val configProvider: ConfigProvider = TypesafeConfigProvider.fromResourcePath()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    ZIO.config[JvmMonitoringConfig](JvmMonitoringConfig.config).flatMap { jvmMonitoringConfig =>
      Server.server
        .provide(
          // controllers
          PartitioningControllerImplV1.layer,
          PartitioningControllerImplV2.layer,
          CheckpointControllerImplV1.layer,
          CheckpointControllerImplV2.layer,
          FlowControllerImplV2.layer,
          // services
          PartitioningServiceImplV1.layer,
          PartitioningServiceImplV2.layer,
          CheckpointServiceImplV1.layer,
          CheckpointServiceImplV2.layer,
          FlowServiceImplV2.layer,
          // repositories
          PartitioningRepositoryImplV1.layer,
          PartitioningRepositoryImplV2.layer,
          CheckpointRepositoryImplV1.layer,
          CheckpointRepositoryImplV2.layer,
          FlowRepositoryImplV2.layer,
          // database
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
          UpdatePartitioningParent.layer,
          GetPartitioningAncestors.layer,
          PostgresDatabaseProvider.layer,
          TransactorProvider.layer,
          // aws
          AwsSecretsProviderImpl.layer,
          // scope
          zio.Scope.default,
          // prometheus
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

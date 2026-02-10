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

package za.co.absa.atum.server.api.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import doobie.hikari.HikariTransactor
import io.prometheus.client.CollectorRegistry
import za.co.absa.atum.server.config.PostgresConfig
import zio.Runtime.defaultBlockingExecutor
import zio._
import zio.interop.catz._

object TransactorProvider {

  val layer: ZLayer[Any with Scope, Throwable, HikariTransactor[Task]] = ZLayer {
    for {
      postgresConfig <- ZIO.config[PostgresConfig](PostgresConfig.config)

      hikariConfig = {
        val dataSourceProperties = new java.util.Properties()
        dataSourceProperties.setProperty("serverName", postgresConfig.serverName)
        dataSourceProperties.setProperty("portNumber", postgresConfig.portNumber.toString)
        dataSourceProperties.setProperty("databaseName", postgresConfig.databaseName)
        dataSourceProperties.setProperty("user", postgresConfig.user)
        dataSourceProperties.setProperty("passwordSecretId", postgresConfig.passwordSecretId)

        val config = new HikariConfig()
        config.setDataSourceClassName(postgresConfig.dataSourceClass)
        config.setDataSourceProperties(dataSourceProperties)

        // Pool settings, especially sizing
        config.setPoolName("DoobiePostgresHikariPool")
        config.setMinimumIdle(postgresConfig.minimumIdle)
        config.setMaximumPoolSize(postgresConfig.maxPoolSize)

        // Connection lifecycle settings
        config.setIdleTimeout(postgresConfig.idleTimeout)
        config.setKeepaliveTime(postgresConfig.keepaliveTime)
        config.setMaxLifetime(postgresConfig.maxLifetime)

        // Misc DB settings
        config.setLeakDetectionThreshold(postgresConfig.leakDetectionThreshold)

        // Prometheus metrics integration
        if (postgresConfig.prometheusMetricsEnabled)
          config.setMetricsTrackerFactory(new PrometheusMetricsTrackerFactory(CollectorRegistry.defaultRegistry))

        config
      }

      xa <- HikariTransactor
        .fromHikariConfig[Task](hikariConfig, defaultBlockingExecutor.asExecutionContext)
        .toScopedZIO
    } yield xa
  }

}

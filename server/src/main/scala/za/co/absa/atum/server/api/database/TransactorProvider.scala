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
import doobie.hikari.HikariTransactor
import za.co.absa.atum.server.config.PostgresConfig
import zio.Runtime.defaultBlockingExecutor
import zio._
import zio.interop.catz._

object TransactorProvider {

  val layer: ZLayer[Any with Scope, Throwable, HikariTransactor[Task]] = ZLayer {
    for {
      postgresConfig <- ZIO.config[PostgresConfig](PostgresConfig.config)
      hikariConfig = {
        val config = new HikariConfig()
        config.setDriverClassName(postgresConfig.dataSourceClass)
        config.setJdbcUrl(
          s"jdbc:postgresql://${postgresConfig.serverName}:${postgresConfig.portNumber}/${postgresConfig.databaseName}"
        )
        config.setUsername(postgresConfig.user)
        config.setPassword(postgresConfig.password)
        config.setMaximumPoolSize(postgresConfig.maxPoolSize)
        config
      }
      xa <- HikariTransactor
        .fromHikariConfig[Task](hikariConfig, defaultBlockingExecutor.asExecutionContext)
        .toScopedZIO
    } yield xa
  }

}

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

package za.co.absa.atum.server.api

import doobie.HC
import doobie.util.transactor.{Strategy, Transactor}
import za.co.absa.atum.server.config.PostgresConfig
import zio._
import zio.interop.catz._

object TestTransactorProvider {

  val layerWithoutRollback: ZLayer[Any, Config.Error, Transactor[Task]] = ZLayer {
    for {
      postgresConfig <- ZIO.config[PostgresConfig](PostgresConfig.config)
      transactor <- ZIO.succeed(
        Transactor.fromDriverManager[Task](
          postgresConfig.dataSourceClass,
          s"jdbc:postgresql://${postgresConfig.serverName}:${postgresConfig.portNumber}/${postgresConfig.databaseName}",
          postgresConfig.user,
          postgresConfig.password
        )
      )
    } yield transactor
  }

  val layerWithRollback: ZLayer[Any, Config.Error, Transactor[Task]] = ZLayer {
    for {
      postgresConfig <- ZIO.config[PostgresConfig](PostgresConfig.config)
      transactor <- ZIO.succeed(
        Transactor.fromDriverManager[Task](
          postgresConfig.dataSourceClass,
          s"jdbc:postgresql://${postgresConfig.serverName}:${postgresConfig.portNumber}/${postgresConfig.databaseName}",
          postgresConfig.user,
          postgresConfig.password
        )
      )
      transactorWithRollback = Transactor.strategy.set(transactor, Strategy.default.copy(after = HC.rollback))
    } yield transactorWithRollback
  }

}

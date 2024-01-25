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

package za.co.absa.atum.server.api.provider

import com.typesafe.config.{Config, ConfigValueFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import za.co.absa.atum.server.api.AtumConfig
import za.co.absa.atum.server.api.database.Runs
import za.co.absa.fadb.slick.FaDbPostgresProfile.api._
import za.co.absa.fadb.slick.SlickPgEngine

import scala.concurrent.ExecutionContext

@Component
class PostgresAccessProvider @Autowired()(atumConfig: AtumConfig) {

  val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  private def databaseConfig: Config = {
    val baseConfig = atumConfig.dbConfig
    // TODO: https://github.com/AbsaOSS/atum-service/issues/107
    Map.empty.foldLeft(baseConfig) { case (acc, (configPath, configVal)) =>
      acc.withValue(configPath, ConfigValueFactory.fromAnyRef(configVal))
    }
  }

  private val db = Database.forConfig("", databaseConfig)

  private implicit val slickPgEngine: SlickPgEngine = new SlickPgEngine(db)(executionContext)
  val runs: Runs = new Runs()
}
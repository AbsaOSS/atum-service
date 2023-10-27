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

package za.co.absa.atum.web.api.provider

import com.typesafe.config.{Config, ConfigFactory}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import slick.jdbc.JdbcBackend.Database
import za.co.absa.atum.web.api.database.Runs
import za.co.absa.fadb.slick.SlickPgEngine

import scala.concurrent.ExecutionContext

/**
 *
 * @param executors
 */
@Component
class PostgresAccessProvider @Autowired()(executor: ExecutionContext) {

  private val config = ConfigFactory.load("application.properties")

  /**
   *
   * @return
   */
  private def dbConfig: Config = {
    val conf = config.getConfig("postgres")
    print("Configs:", conf)
    conf
  }

  private val db: Database =  Database.forConfig("", dbConfig)

  private implicit val slickPgEngine: SlickPgEngine = new SlickPgEngine(db)(executor)
  val runs: Runs = new Runs()

}

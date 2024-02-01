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
import za.co.absa.atum.server.api.{AtumConfig, RetrieveAwsSecret}
import za.co.absa.atum.server.api.database.Runs
import za.co.absa.fadb.slick.FaDbPostgresProfile.api._
import za.co.absa.fadb.slick.SlickPgEngine
import scala.concurrent.ExecutionContext

import scala.util.Try

@Component
class PostgresAccessProvider @Autowired()(atumConfig: AtumConfig) {

  val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  private val awsProfile = atumConfig.awsProfile
  val retrieveAwsSecret: RetrieveAwsSecret = new RetrieveAwsSecret(awsProfile)

  private val secretsSection = "fromSecrets"

  private val connectionPool = "connectionPool"
  private val dataSourceClass = "dataSourceClass"
  private val serverName = "properties.serverName"
  private val portNumber = "properties.portNumber"
  private val databaseName = "properties.databaseName"
  private val user = "properties.user"
  private val password = "properties.password"

  private def getKeysForSecrets(secretsConfig: Config): Map[String, String] = {
    import PostgresAccessProvider._

    Map
      .empty[String, String]
      .addSecretName(secretsConfig, connectionPool)
      .addSecretName(secretsConfig, dataSourceClass)
      .addSecretName(secretsConfig, serverName)
      .addSecretName(secretsConfig, portNumber)
      .addSecretName(secretsConfig, databaseName)
      .addSecretName(secretsConfig, user)
      .addSecretName(secretsConfig, password)
  }

  private def overrideWithSecret(oldConfig: Config, path: String, secretName: String): Config = {

    val secretString: String = retrieveAwsSecret.retrieveAwsSecret(secretName)
    val overrideValue = secretString.foldLeft(Try("")) {(acc, s) =>
      acc.flatMap(str => Try(str + s))
    }

    overrideValue
      .map(value => oldConfig.withValue(path, ConfigValueFactory.fromAnyRef(value)))
      .getOrElse(oldConfig)
  }

  private def databaseConfig: Config = {
    val baseConfig = atumConfig.dbConfig
    val keysForSecretes = if (baseConfig.hasPath(secretsSection)) {
      getKeysForSecrets(baseConfig.getConfig(secretsSection))
    } else {
      Map.empty
    }

    keysForSecretes.foldLeft(baseConfig) { case (acc, (path, secretName)) =>
      overrideWithSecret(acc, path, secretName)
    }
  }

  private val db = Database.forConfig("", databaseConfig)
  private implicit val slickPgEngine: SlickPgEngine = new SlickPgEngine(db)(executionContext)
  val runs: Runs = new Runs()

}

object PostgresAccessProvider {
  private implicit class MapAddingSecret(val map: Map[String, String]) extends AnyVal {
    def addSecretName(config: Config, path: String): Map[String, String] = {
      if (config.hasPath(path)) {
        map + (path -> config.getString(path))
      } else {
        map
      }
    }
  }
}

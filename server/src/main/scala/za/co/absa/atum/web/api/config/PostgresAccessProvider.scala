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

package za.co.absa.atum.web.api.config

import com.typesafe.config.{Config, ConfigFactory}
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.stereotype.Component
import slick.jdbc.JdbcBackend.Database
import za.co.absa.atum.web.api.repositories.Checkpoint
import za.co.absa.atum.web.api.service.utils.ExecutorsProvider
import za.co.absa.fadb.slick.SlickPgEngine

import scala.beans.BeanProperty
//import scala.util.Try

@Component
class PostgresAccessProvider@Autowired()(
                                          @BeanProperty @Value("${postgres:{}}")
                                          var extraPropertiesJson: String,
                                          executors: ExecutorsProvider
                                        ) {

//  private val secretsSection = "fromSecrets"

//  private val connectionPool = "connectionPool"
//  private val dataSourceClass = "dataSourceClass"
//  private val serverName = "serverName"
//  private val portNumber = "portNumber"
//  private val databaseName = "databaseName"
//  private val user = "user"
//  private val password = "password"

//  private val extraProperties = JacksonHelper
//    .objectMapper
//    .readValue(extraPropertiesJson, classOf[AWSServiceExtraProperties])
//  private val aws = AWSFactory.build(extraProperties, executors)

//  private def getKeysForSecrets(secretsConfig: Config): Map[String, String] = {
//    import PostgresAccessProvider._
//
//    Map
//      .empty[String, String]
//      .addSecretName(secretsConfig, connectionPool)
//      .addSecretName(secretsConfig, dataSourceClass)
//      .addSecretName(secretsConfig, serverName)
//      .addSecretName(secretsConfig, portNumber)
//      .addSecretName(secretsConfig, databaseName)
//      .addSecretName(secretsConfig, user)
//      .addSecretName(secretsConfig, password)
//  }

//  def overrideWithSecret(oldConfig: Config, path: String, secretName: String): Config = {
//
//    val overrideValue: Try[String] = aws.getSecretValue(secretName)
//
//    overrideValue
//      .map(value => oldConfig.withValue(path, ConfigValueFactory.fromAnyRef(value)))
//      .getOrElse(oldConfig)
//  }

  private val config = ConfigFactory
    .load("application.properties")

  def dbConfig: Config = {
    val conf = config.getConfig("postgres")
    print("Configs:", conf)
    conf
  }

//  private def databaseConfig: Config = {
//    val baseConfig = AtumServiceConfig.dbConfig
//    val keysForSecretes = if (baseConfig.hasPath(secretsSection)) {
//      getKeysForSecrets(baseConfig.getConfig(secretsSection))
//    } else {
//      Map.empty
//    }
//    print(keysForSecretes)
//    keysForSecretes
////    keysForSecretes.foldLeft(baseConfig) {case (acc, (path, secretName)) =>
////      overrideWithSecret(acc, path, secretName)
////    }
//  }

  private val db: Database = Database.forConfig("", dbConfig)
  private implicit val slickPgEngine: SlickPgEngine = new SlickPgEngine(db)(executors.cpuBoundExecutionContext)
  val checkpoint: Checkpoint = new Checkpoint
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

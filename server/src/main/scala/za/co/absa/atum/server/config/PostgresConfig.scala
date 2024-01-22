package za.co.absa.atum.server.config

import zio.Config
import zio.config.magnolia.deriveConfig

case class PostgresConfig(
                           dataSourceClass: String,
                           serverName: String,
                           portNumber: Int,
                           databaseName: String,
                           user: String,
                           password: String,
                           maxPoolSize: Int
                         )

object PostgresConfig {
  val config: Config[PostgresConfig] = deriveConfig[PostgresConfig].nested("postgres")
}

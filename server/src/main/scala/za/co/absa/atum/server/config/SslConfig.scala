package za.co.absa.atum.server.config

import zio.Config
import zio.config.magnolia.deriveConfig

case class SslConfig(
  enabled: Boolean,
  keyStorePassword: String,
  keyStorePath: String
)

object SslConfig {
  val config: Config[SslConfig] = deriveConfig[SslConfig].nested("ssl")
}

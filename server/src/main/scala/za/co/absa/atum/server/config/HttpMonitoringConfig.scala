package za.co.absa.atum.server.config

import zio.Config
import zio.config.magnolia.deriveConfig

case class HttpMonitoringConfig(enabled: Boolean)

object HttpMonitoringConfig {
  val config: Config[HttpMonitoringConfig] = deriveConfig[HttpMonitoringConfig].nested("monitoring", "http")
}

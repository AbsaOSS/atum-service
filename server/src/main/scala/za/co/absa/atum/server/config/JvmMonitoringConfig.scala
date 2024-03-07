package za.co.absa.atum.server.config

import zio.Config
import zio.config.magnolia.deriveConfig

case class JvmMonitoringConfig(
                                enabled: Boolean,
                                intervalInSeconds: Int
                              )

object JvmMonitoringConfig {
  val config: Config[JvmMonitoringConfig] = deriveConfig[JvmMonitoringConfig].nested("monitoring","jvm")
}

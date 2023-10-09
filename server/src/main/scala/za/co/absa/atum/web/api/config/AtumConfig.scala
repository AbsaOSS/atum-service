package za.co.absa.atum.web.api.config

import com.typesafe.config.{Config, ConfigFactory}
//import za.co.absa.commons.s3.SimpleS3Location.SimpleS3LocationExt

trait AtumConfig {

  def dbConfig: Config

}

object AtumConfig extends AtumConfig {

  override def dbConfig: Config = config.getConfig("postgres")

  private val config = ConfigFactory
    .load("application.properties")

}

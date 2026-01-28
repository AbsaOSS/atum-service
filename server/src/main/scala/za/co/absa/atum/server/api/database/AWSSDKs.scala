package za.co.absa.atum.server.api.database

import com.typesafe.config.ConfigFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient

object AWSSDKs {

  private val config = ConfigFactory
    .load()
    .getConfig(s"aws")

  private val defaultRegion = config.getString("region")

  private val credentialsProvider = DefaultCredentialsProvider.create

  val secretsManagerSyncClient: SecretsManagerClient = SecretsManagerClient.builder
    .region(Region.of(defaultRegion))
    .credentialsProvider(credentialsProvider)
    .build

}

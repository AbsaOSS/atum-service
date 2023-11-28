package za.co.absa.atum.server.api

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, SecretsManagerException}

object RetrieveAwsSecret {
  def retrieveAwsSecret(): Unit = {
    val secretName = "atum_user"

    // Create a Secrets Manager client
    val client = SecretsManagerClient.builder()
      .region(Region.AF_SOUTH_1)
      .build()

    try {
      val request = GetSecretValueRequest.builder()
        .secretId(secretName)
        .build()

      val response = client.getSecretValue(request)

      response.secretString.foreach { secretString =>
        println(s"Secret Key: $secretString")
      }
    } catch {
      case e: SecretsManagerException =>
        println(s"Error retrieving secret key: ${e.getMessage}")
    } finally {
      // Close the client when done
      client.close()
    }
  }
}

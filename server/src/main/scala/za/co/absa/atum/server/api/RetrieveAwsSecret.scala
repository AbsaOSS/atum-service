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

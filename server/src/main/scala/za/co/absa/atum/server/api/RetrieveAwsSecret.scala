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

/**
 * Class implement the functionality of retrieving secret keys from aws secret manger service
 */
class RetrieveAwsSecret {
  private val secretsManagerClient: SecretsManagerClient = SecretsManagerClient.builder()
    .region(Region.AF_SOUTH_1)
    .build()

  /**
   * Function retrieves secret keys from aws
   * @param secretName
   * @return
   */
  def retrieveAwsSecret(secretName: String = "atum_user"): Seq[String] = {
    try {
      val request = GetSecretValueRequest.builder()
        .secretId(secretName)
        .build()

      val response = secretsManagerClient.getSecretValue(request)

      response.secretString.foreach { secretString =>
        println(s"Secret Key: $secretString")
      }
      response.secretString.map(_.toString)
    } catch {
      case e: SecretsManagerException =>
        println(s"Error retrieving secret key: ${e.getMessage}")
        e.getMessage.map(_.toString)
    } finally {
      // Close the client when done
      secretsManagerClient.close()
    }
  }
}

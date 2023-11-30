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


import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, GetSecretValueResponse, SecretsManagerException}

class RetrieveAwsSecretTest extends AnyFlatSpec with BeforeAndAfterEach{

  class MockSecretsManagerClient extends SecretsManagerClient {
     override def getSecretValue(request: GetSecretValueRequest): GetSecretValueResponse = {
      // Simulate a response from AWS Secrets Manager
      if (request.secretId() == "ValidSecretName") {
        GetSecretValueResponse.builder().secretString("ValidSecretKey").build()
      } else {
        throw SecretsManagerException.builder().message("Invalid secret ID").build()
      }
    }

    override def serviceName(): String = "SecretManager"

    override def close(): Unit = close()
  }

  var extractor: RetrieveAwsSecret = _

  override def beforeEach(): Unit = {
    // Initialize the object with a mock client for testing
    val mockClient = new MockSecretsManagerClient()
    extractor = new RetrieveAwsSecret()
  }

  "AWSSecretKeyExtractor" should "retrieve secret key from AWS Secrets Manager" in {
    val secretName = "ValidSecretName"
    val secretKey = extractor.retrieveAwsSecret(secretName)
    println("Secret key: ", secretKey)
  }

  it should "return None for an invalid secret name" in {
    val secretName = "InvalidSecretName"
    val secretKey = extractor.retrieveAwsSecret(secretName)
    println("Secret key: " + secretKey.map(_.toSeq))
  }
}

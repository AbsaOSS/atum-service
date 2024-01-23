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

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, GetSecretValueResponse, SecretsManagerException}


class RetrieveAwsSecretSpec extends AnyFlatSpec with MockitoSugar {

  "retrieveAwsSecret" should "return secret keys from AWS" in {
    val mockSecretsManagerClient = mock[SecretsManagerClient]
    val retrieveAwsSecret = new RetrieveAwsSecret("testProfile") {
      override val secretsManagerClient: SecretsManagerClient = mockSecretsManagerClient
    }
    val testSecretName: String = "testSecret"
    val expectedResults = Vector("t", "e", "s", "t", "S", "e", "c", "r", "e", "t")

    val mockResponse = GetSecretValueResponse.builder().secretString(testSecretName).build()
    when(mockSecretsManagerClient.getSecretValue(any[GetSecretValueRequest])).thenReturn(mockResponse)

    val actualResults = retrieveAwsSecret.retrieveAwsSecret(testSecretName)
    assert(actualResults == expectedResults)
  }

  it should "handle SecretsManagerException and return error message" in {
    val mockSecretsManagerClient = mock[SecretsManagerClient]
    val retrieveAwsSecret = new RetrieveAwsSecret("testProfile") {
      override val secretsManagerClient: SecretsManagerClient = mockSecretsManagerClient
    }

    val testSecretName: String = "testSecret"

    val exception = SecretsManagerException.builder().message("testError").build()
    when(mockSecretsManagerClient.getSecretValue(any[GetSecretValueRequest])).thenThrow(exception)

    assert(retrieveAwsSecret.retrieveAwsSecret(testSecretName) == "testError".map(_.toString))
  }

}

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
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, GetSecretValueResponse, SecretsManagerException}

import scala.util.{Failure, Success, Try}


class RetrieveAwsSecretSpec extends AnyFlatSpec with Matchers with MockitoSugar {
  val secretsManagerClient: SecretsManagerClient = mock[SecretsManagerClient]
  val retrieveAwsSecret: RetrieveAwsSecret = new RetrieveAwsSecret("test-profile") {
    private[RetrieveAwsSecret] val secretsManagerClient: SecretsManagerClient = secretsManagerClient
  }

  "RetrieveAwsSecret" should "return a sequence of strings from AWS secret service" in {
    val secretName = "test-secret"
    val secretString = "test-secret-key"
    val response: GetSecretValueResponse = GetSecretValueResponse.builder().secretString(secretString).build()

    when(secretsManagerClient.getSecretValue(any[GetSecretValueRequest])).thenReturn(response)

    val result: Seq[String] = retrieveAwsSecret.retrieveAwsSecret(secretName)
    val resultsToTry = secretString.foldLeft(Try("")) { (acc, s) =>
      acc.flatMap(str => Try(str + s))
    }

    resultsToTry shouldBe Success(Seq(secretString))
  }

  it should "return an error message when there is an exception" in {
    val secretName = "test-secret"
    val exceptionMessage = "test-exception-message"
    val exception = SecretsManagerException.builder().message(exceptionMessage).build()

    when(secretsManagerClient.getSecretValue(any[GetSecretValueRequest])).thenThrow(exception)

    val secretString: Seq[String] = retrieveAwsSecret.retrieveAwsSecret(secretName)

    val resultsToTry = secretString.foldLeft(Try("")) { (acc, s) =>
      acc.flatMap(str => Try(str + s))
    }

    resultsToTry shouldBe Failure(exception)
  }
}

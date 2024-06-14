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

package za.co.absa.atum.server.aws

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, GetSecretValueResponse}
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.config.AwsConfig
import zio.test._
import zio.{Scope, ZIO, ZLayer}

object AwsSecretsProviderUnitTests extends ConfigProviderTest {

  private val secretsManagerClientMock = mock(classOf[SecretsManagerClient])

  private val dummySecretValue = "expectedValue"
  private val mockedResponse = GetSecretValueResponse.builder().secretString(dummySecretValue).build()

  when(secretsManagerClientMock.getSecretValue(any[GetSecretValueRequest]())).thenReturn(mockedResponse)

  private val testAwsSecretsProviderLayer = ZLayer.succeed(new AwsSecretsProviderImpl(secretsManagerClientMock))

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("AwsSecretsProviderSuite")(
      test("GetSecretValue returns expected secret's value"){
        for {
          awsConfig <- ZIO.config[AwsConfig](AwsConfig.config)
          awsSecretValue <- AwsSecretsProvider.getSecretValue(awsConfig.dbPasswordSecretName)
        } yield assertTrue(dummySecretValue == awsSecretValue)
      }
    )

  }.provide(
    testAwsSecretsProviderLayer
  )

}

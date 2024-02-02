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

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import za.co.absa.atum.server.config.AwsConfig
import zio._
import zio.macros.accessible

@accessible
trait AwsSecretsProvider {
  def getSecretValue(secretName: String): Task[String]
}

class AwsSecretsProviderImpl(secretsManagerClient: SecretsManagerClient) extends AwsSecretsProvider {
  override def getSecretValue(secretName: String): Task[String] = {
    ZIO.attempt {
      val getSecretValueRequest = GetSecretValueRequest.builder().secretId(secretName).build()
      secretsManagerClient.getSecretValue(getSecretValueRequest).secretString()
    }
  }
}

object AwsSecretsProviderImpl {
  val layer: ZLayer[Any, Config.Error, AwsSecretsProviderImpl] = ZLayer {
    for {
      awsConfig <- ZIO.config[AwsConfig](AwsConfig.config)
    } yield new AwsSecretsProviderImpl(
      SecretsManagerClient.builder().region(Region.of(awsConfig.region)).build()
    )
  }
}

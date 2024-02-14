package za.co.absa.atum.server.aws

import zio.Task
import zio.macros.accessible

@accessible
trait AwsSecretsProvider {
  def getSecretValue(secretName: String): Task[String]
}

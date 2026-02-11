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

package za.co.absa.atum.server.api.database

import com.typesafe.config.ConfigFactory
import org.postgresql.ds.PGSimpleDataSource
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest

import java.sql.{Connection, SQLException}
import scala.util.{Failure, Try}
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *  `PGSimpleDataSource` but with password being fetched from AWS Secrets Manager.
 *
 *  Refreshes the password when `PGSimpleDataSource.getConnection` fails,
 *  thus it works with secrets with rotation enabled.
 *
 *  Expects the same set of properties as `PGSimpleDataSource` +
 *  `passwordSecretId` - ID of the secret containing password.
 */
class PostgresDataSourceWithPasswordFromSecretsManager extends PGSimpleDataSource {

  protected val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private var password: String = _
  private var passwordSecretId: String = _

  override def getConnection: Connection = {
    if (Option(password).isEmpty) {
      val pw = getPasswordFromSecretsManagerOrConfig
      setInternalPassword(pw)
    }

    val connectionTry = Try(baseGetConnection(user, password)).recoverWith { case _: SQLException =>
      logger.info("Failed to create Postgres connection, attempting to refresh the password and try again...")
      for {
        passwordFromSecretsManager <- getPasswordFromSecretsManager
        connection <- Try(baseGetConnection(user, passwordFromSecretsManager)).recoverWith { case e =>
          logger.error("Failed to create Postgres connection even after password refresh")
          Failure(e)
        }
      } yield {
        setInternalPassword(passwordFromSecretsManager)
        connection
      }
    }

    connectionTry.get
  }

  // getter and setter for passwordSecretId are needed as this class is usually constructed by reflection
  def getPasswordSecretId: String = passwordSecretId

  def setPasswordSecretId(passwordSecretId: String): Unit = {
    this.passwordSecretId = passwordSecretId
  }

  override def setProperty(name: String, value: String): Unit = name match {
    case "passwordSecretId" => setPasswordSecretId(value)
    case _ => baseSetProperty(name, value)
  }

  // the following protected defs are for easier unit tests
  protected def baseSetProperty(name: String, value: String): Unit = super.setProperty(name, value)
  protected def baseGetConnection(username: String, password: String): Connection =
    super.getConnection(username, password)
  protected def user: String = this.getUser
  protected def secretsManagerClient: SecretsManagerClient = AWSSDKs.secretsManagerSyncClient

  private[database] def setInternalPassword(password: String): Unit = {
    this.password = password
  }

  private def getPasswordFromSecretsManager: Try[String] = {
    val secretID = getPasswordSecretId

    val secretValueTry = Try {
      logger.info(s"Fetching password for Postgres from Secrets Manager for user ${this.user} (secret id: $secretID)")
      val response = secretsManagerClient.getSecretValue(
        GetSecretValueRequest.builder
          .secretId(secretID)
          .build
      )
      logger.info("Successfully fetched password for Postgres from Secrets Manager")
      response.secretString
    }

    secretValueTry.recoverWith { case e =>
      logger.error(s"Failed to fetch password for Postgres from Secrets Manager (secret id: $secretID)")
      Failure(e)
    }
  }

  private def getPasswordFromSecretsManagerOrConfig: String = {
    getPasswordFromSecretsManager.getOrElse {
      logger.error(
        s"Failed to fetch password from Secrets Manager (secret id: ${getPasswordSecretId}). " +
          s"Falling back to config value."
      )
      val configPassword = ConfigFactory.load().getConfig("postgres").getString("password")
      logger.info(s"Successfully fetched password for Postgres from config")
      configPassword
    }
  }

}

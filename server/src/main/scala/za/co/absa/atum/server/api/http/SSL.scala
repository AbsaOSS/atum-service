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

package za.co.absa.atum.server.api.http

import za.co.absa.atum.server.config.SslConfig
import zio.ZIO

import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object SSL {

  def context: ZIO[Any, Throwable, SSLContext] = {
    for {
      sslConfig <- ZIO.config[SslConfig](SslConfig.config)
      _ <- ZIO.logDebug("Attempting to initialize SSLContext")
      keyStore <- ZIO.attempt {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
        val in = new FileInputStream(sslConfig.keyStorePath)
        keyStore.load(in, sslConfig.keyStorePassword.toCharArray)
        keyStore
      }
      _ <- ZIO.logDebug("KeyStore instantiated")
      keyManagerFactory <- ZIO.attempt {
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        keyManagerFactory.init(keyStore, sslConfig.keyStorePassword.toCharArray)
        keyManagerFactory
      }
      _ <- ZIO.logDebug("KeyManagerFactory initialized")
      trustManagerFactory <- ZIO.attempt {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
        trustManagerFactory.init(keyStore)
        trustManagerFactory
      }
      _ <- ZIO.logDebug("TrustManagerFactory initialized")
      sslContext <- ZIO.attempt {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom())
        sslContext
      }
      _ <- ZIO.logDebug("SSLContext successfully initialized")
    } yield sslContext
  }

}

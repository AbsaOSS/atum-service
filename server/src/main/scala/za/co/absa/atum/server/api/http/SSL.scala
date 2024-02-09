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
      keyStore <- ZIO.attempt {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
        val in = new FileInputStream(sslConfig.keyStorePath)
        keyStore.load(in, sslConfig.keyStorePassword.toCharArray)
        keyStore
      }
      keyManagerFactory <- ZIO.attempt {
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        keyManagerFactory.init(keyStore, sslConfig.keyStorePassword.toCharArray)
        keyManagerFactory
      }
      trustManagerFactory <- ZIO.attempt {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
        trustManagerFactory.init(keyStore)
        trustManagerFactory
      }
      sslContext <- ZIO.attempt {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom())
        sslContext
      }
    } yield sslContext
  }

}

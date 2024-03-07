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

import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import za.co.absa.atum.server.config.{HttpMonitoringConfig, JvmMonitoringConfig, SslConfig}
import zio._
import zio.interop.catz._

import javax.net.ssl.SSLContext

trait Server extends Routes {

  private def createServer(port: Int, sslContext: Option[SSLContext] = None): ZIO[HttpEnv.Env, Throwable, Unit] =
    for {
      executor <- ZIO.executor
      httpMonitoringConfig <- ZIO.config[HttpMonitoringConfig](HttpMonitoringConfig.config)
      jvmMonitoringConfig <- ZIO.config[JvmMonitoringConfig](JvmMonitoringConfig.config)
      builder = BlazeServerBuilder[HttpEnv.F]
        .bindHttp(port, "0.0.0.0")
        .withExecutionContext(executor.asExecutionContext)
        .withHttpApp(Router("/" -> allRoutes(httpMonitoringConfig, jvmMonitoringConfig)).orNotFound)
      builderWithSsl = sslContext.fold(builder)(ctx => builder.withSslContext(ctx))
      _ <- builderWithSsl.serve.compile.drain
    } yield ()

  private val httpServer = createServer(8080)
  private val httpsServer = SSL.context.flatMap(context => createServer(8443, Some(context)))

  protected val server: ZIO[HttpEnv.Env, Throwable, Unit] = for {
    sslConfig <- ZIO.config[SslConfig](SslConfig.config)
    server <- if (sslConfig.enabled) httpsServer else httpServer
  } yield server

}

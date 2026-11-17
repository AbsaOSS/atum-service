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

package za.co.absa.atum.server.api.v2.http

import org.http4s.{Method, Request, Uri}
import org.mockito.Mockito.mock
import sttp.model.StatusCode
import za.co.absa.atum.server.api.common.http.{HttpEnv, Routes}
import za.co.absa.atum.server.api.v1.controller.{
  CheckpointController => CheckpointControllerV1,
  PartitioningController => PartitioningControllerV1
}
import za.co.absa.atum.server.api.v2.controller.{CheckpointController, FlowController, PartitioningController}
import za.co.absa.atum.server.config.{HikariMonitoringConfig, HttpMonitoringConfig, JvmMonitoringConfig}
import zio.interop.catz._
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.{Task, ULayer, ZIO, ZLayer}

object PartitioningSearchTestSupport {
  private lazy val routes =
    Routes.allRoutes(
      HttpMonitoringConfig(enabled = false),
      HikariMonitoringConfig(enabled = false),
      JvmMonitoringConfig(enabled = false, intervalInSeconds = 1)
    )

  // Exercise the registered route order without opening a socket or starting a server.
  def get(uri: sttp.model.Uri, controller: PartitioningController): Task[(StatusCode, String)] = {
    val environment: ULayer[HttpEnv.Env] =
      ZLayer.succeed(controller) ++
        ZLayer.succeed(mock(classOf[CheckpointController])) ++
        ZLayer.succeed(mock(classOf[FlowController])) ++
        ZLayer.succeed(mock(classOf[PartitioningControllerV1])) ++
        ZLayer.succeed(mock(classOf[CheckpointControllerV1])) ++
        ZLayer.succeed(mock(classOf[PrometheusPublisher]))

    (for {
      requestUri <- ZIO.fromEither(Uri.fromString(uri.toString))
      responseOption <- routes.run(Request[HttpEnv.F](method = Method.GET, uri = requestUri)).value
      response <- ZIO.getOrFailWith(new IllegalStateException(s"No route matched $uri"))(responseOption)
      body <- response.bodyText.compile.string
    } yield (StatusCode(response.status.code), body)).provideLayer(environment)
  }
}

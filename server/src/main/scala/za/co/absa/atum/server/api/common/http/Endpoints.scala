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

package za.co.absa.atum.server.api.common.http

import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._
import sttp.tapir.{PublicEndpoint, endpoint}
import za.co.absa.atum.model.ApiPaths._
import za.co.absa.atum.model.dto.BuildInfoDTO
import za.co.absa.atum.model.envelopes.StatusResponse
import zio.ZIO

object Endpoints extends BaseEndpoints {

  val zioMetricsEndpoint: PublicEndpoint[Unit, Unit, String, Any] = {
    endpoint.get.in(ZioMetrics).out(stringBody)
  }

  val healthEndpoint: PublicEndpoint[Unit, Unit, StatusResponse, Any] =
    endpoint.get.in(Health).out(jsonBody[StatusResponse].example(StatusResponse.up))

  val readinessEndpoint: PublicEndpoint[Unit, Unit, StatusResponse, Any] =
    endpoint.get.in(Health/Readiness).out(jsonBody[StatusResponse])

  val livenessEndpoint: PublicEndpoint[Unit, Unit, StatusResponse, Any] =
    endpoint.get.in(Health/Liveness).out(jsonBody[StatusResponse])

  val buildInfoEndpoint: PublicEndpoint[Unit, Unit, BuildInfoDTO, Any] =
    endpoint.get.in(BuildInfo).out(jsonBody[BuildInfoDTO])

  private final val healthLogic = (_: Unit) => ZIO.succeed(StatusResponse.up)

  private final val buildInfoLogic = (_: Unit) => ZIO.succeed(
    BuildInfoDTO(
      version = za.co.absa.atum.server.api.common.http.BuildInfo.version,
      fullVersion = za.co.absa.atum.server.api.common.http.BuildInfo.fullVersion
    )
  )

  val serverEndpoints: List[ZServerEndpoint[HttpEnv.Env, Any]] = List(
    createServerEndpoint(healthEndpoint, healthLogic),
    createServerEndpoint(readinessEndpoint, healthLogic),
    createServerEndpoint(livenessEndpoint, healthLogic),
    createServerEndpoint(buildInfoEndpoint, buildInfoLogic),
  )

}

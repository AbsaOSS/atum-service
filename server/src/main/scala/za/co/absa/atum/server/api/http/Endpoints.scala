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

import sttp.model.StatusCode
import sttp.tapir.{PublicEndpoint, endpoint}
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.play.jsonBody
import sttp.tapir.ztapir._
import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, AtumContextDTO, CheckpointQueryDTO, CheckpointQueryResultDTO, CheckpointSubmitDTO, PartitioningSubmitDTO}
import za.co.absa.atum.server.Constants.Endpoints._
import za.co.absa.atum.server.model.ErrorResponse
import za.co.absa.atum.server.model.PlayJsonImplicits._

trait Endpoints extends BaseEndpoints {

  protected val createCheckpointEndpoint: PublicEndpoint[CheckpointSubmitDTO, ErrorResponse, CheckpointSubmitDTO, Any] = {
    apiV1.post
      .in(CreateCheckpoint)
      .in(jsonBody[CheckpointSubmitDTO])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[CheckpointSubmitDTO])
  }

  protected val createPartitioningEndpoint
    : PublicEndpoint[PartitioningSubmitDTO, ErrorResponse, AtumContextDTO, Any] = {
    apiV1.post
      .in(CreatePartitioning)
      .in(jsonBody[PartitioningSubmitDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[AtumContextDTO])
  }

  protected val createOrUpdateAdditionalDataEndpoint
    : PublicEndpoint[AdditionalDataSubmitDTO, ErrorResponse, AdditionalDataSubmitDTO, Any] = {
    apiV1.post
      .in(CreateOrUpdateAdditionalData)
      .in(jsonBody[AdditionalDataSubmitDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[AdditionalDataSubmitDTO])
  }

  protected val getPartitioningCheckpointsEndpoint: PublicEndpoint[CheckpointQueryDTO, ErrorResponse, CheckpointQueryResultDTO, Any] = {
    apiV2.get
      .in(GetPartitioningCheckpoints)
      .in(jsonBody[CheckpointQueryDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[CheckpointQueryResultDTO])
  }

  protected val zioMetricsEndpoint: PublicEndpoint[Unit, Unit, String, Any] = {
    endpoint.get.in(ZioMetrics).out(stringBody)
  }

  protected val healthEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] =
    endpoint.get.in(Health)

}

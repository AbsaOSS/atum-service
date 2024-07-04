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
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.ztapir._
import sttp.tapir.json.circe.jsonBody
import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.Constants.Endpoints._
import za.co.absa.atum.server.model.ErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import sttp.tapir.{PublicEndpoint, endpoint}


trait Endpoints extends BaseEndpoints {

  protected val createCheckpointEndpointV1: PublicEndpoint[CheckpointDTO, ErrorResponse, CheckpointDTO, Any] = {
    apiV1.post
      .in(pathToAPIv1CompatibleFormat(CreateCheckpoint))
      .in(jsonBody[CheckpointDTO])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[CheckpointDTO])
  }

  protected val createCheckpointEndpointV2: PublicEndpoint[CheckpointDTO, ErrorResponse, SingleSuccessResponse[CheckpointDTO], Any] = {
    apiV2.post
      .in(CreateCheckpoint)
      .in(jsonBody[CheckpointDTO])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[SingleSuccessResponse[CheckpointDTO]])
  }

  protected val createPartitioningEndpointV1: PublicEndpoint[PartitioningSubmitDTO, ErrorResponse, AtumContextDTO, Any] = {
    apiV1.post
      .in(pathToAPIv1CompatibleFormat(CreatePartitioning))
      .in(jsonBody[PartitioningSubmitDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[AtumContextDTO])
  }

  protected val createPartitioningEndpointV2: PublicEndpoint[PartitioningSubmitDTO, ErrorResponse, SingleSuccessResponse[AtumContextDTO], Any] = {
    apiV2.post
      .in(CreatePartitioning)
      .in(jsonBody[PartitioningSubmitDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[AtumContextDTO]])
  }

  protected val createOrUpdateAdditionalDataEndpointV2: PublicEndpoint[AdditionalDataSubmitDTO, ErrorResponse, SingleSuccessResponse[AdditionalDataSubmitDTO], Any] = {
    apiV2.post
      .in(CreateOrUpdateAdditionalData)
      .in(jsonBody[AdditionalDataSubmitDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[AdditionalDataSubmitDTO]])
  }

  protected val getPartitioningCheckpointsEndpointV2: PublicEndpoint[CheckpointQueryDTO, ErrorResponse, MultiSuccessResponse[CheckpointDTO], Any] = {
    apiV2.get
      .in(GetPartitioningCheckpoints)
      .in(jsonBody[CheckpointQueryDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[MultiSuccessResponse[CheckpointDTO]])
  }

  protected val getFlowCheckpointsEndpointV2: PublicEndpoint[CheckpointQueryDTO, ErrorResponse, MultiSuccessResponse[CheckpointDTO], Any] = {
    apiV2.post
      .in(GetFlowCheckpoints)
      .in(jsonBody[CheckpointQueryDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[MultiSuccessResponse[CheckpointDTO]])
  }

  protected val zioMetricsEndpoint: PublicEndpoint[Unit, Unit, String, Any] = {
    endpoint.get.in(ZioMetrics).out(stringBody)
  }

  protected val healthEndpoint: PublicEndpoint[Unit, Unit, Unit, Any] =
    endpoint.get.in(Health)
}

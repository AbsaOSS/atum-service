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
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.SuccessResponse._
import sttp.tapir.{PublicEndpoint, Validator, endpoint}
import za.co.absa.atum.model.envelopes.{ErrorResponse, StatusResponse}
import za.co.absa.atum.model.ApiPaths._

import java.util.UUID

trait Endpoints extends BaseEndpoints {

  protected val createCheckpointEndpointV1: PublicEndpoint[CheckpointDTO, ErrorResponse, CheckpointDTO, Any] = {
    apiV1.post
      .in(V1Paths.CreateCheckpoint)
      .in(jsonBody[CheckpointDTO])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[CheckpointDTO])
      .errorOutVariantPrepend(conflictErrorOneOfVariant)
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val postCheckpointEndpointV2
    : PublicEndpoint[(Long, CheckpointV2DTO), ErrorResponse, (SingleSuccessResponse[CheckpointV2DTO], String), Any] = {
    apiV2.post
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Checkpoints)
      .in(jsonBody[CheckpointV2DTO])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[SingleSuccessResponse[CheckpointV2DTO]])
      .out(header[String]("Location"))
      .errorOutVariantPrepend(conflictErrorOneOfVariant)
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val createPartitioningEndpointV1
    : PublicEndpoint[PartitioningSubmitDTO, ErrorResponse, AtumContextDTO, Any] = {
    apiV1.post
      .in(V1Paths.CreatePartitioning)
      .in(jsonBody[PartitioningSubmitDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[AtumContextDTO])
  }

  protected val postPartitioningEndpointV2: PublicEndpoint[
    PartitioningSubmitV2DTO,
    ErrorResponse,
    (SingleSuccessResponse[PartitioningWithIdDTO], String),
    Any
  ] = {
    apiV2.post
      .in(V2Paths.Partitionings)
      .in(jsonBody[PartitioningSubmitV2DTO])
      .out(statusCode(StatusCode.Created))
      .out(jsonBody[SingleSuccessResponse[PartitioningWithIdDTO]])
      .out(header[String]("Location"))
      .errorOutVariantPrepend(conflictErrorOneOfVariant)
  }

  protected val getPartitioningAdditionalDataEndpointV2
    : PublicEndpoint[Long, ErrorResponse, SingleSuccessResponse[AdditionalDataDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.AdditionalData)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[AdditionalDataDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val patchPartitioningAdditionalDataEndpointV2
    : PublicEndpoint[(Long, AdditionalDataPatchDTO), ErrorResponse, SingleSuccessResponse[
      AdditionalDataDTO
    ], Any] = {
    apiV2.patch
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.AdditionalData)
      .in(jsonBody[AdditionalDataPatchDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[AdditionalDataDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val getPartitioningEndpointV2
    : PublicEndpoint[String, ErrorResponse, SingleSuccessResponse[
      PartitioningWithIdDTO
    ], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings)
      .in(query[String]("partitioning").description("base64 encoded json representation of partitioning"))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[PartitioningWithIdDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val getPartitioningCheckpointEndpointV2
    : PublicEndpoint[(Long, UUID), ErrorResponse, SingleSuccessResponse[CheckpointV2DTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Checkpoints / path[UUID]("checkpointId"))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[CheckpointV2DTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val getPartitioningCheckpointsEndpointV2
  : PublicEndpoint[(Long, Option[Int], Option[Long], Option[String]), ErrorResponse, PaginatedResponse[
    CheckpointV2DTO
  ], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Checkpoints)
      .in(query[Option[Int]]("limit").default(Some(10)).validateOption(Validator.inRange(1, 1000)))
      .in(query[Option[Long]]("offset").default(Some(0L)).validateOption(Validator.min(0L)))
      .in(query[Option[String]]("checkpoint-name"))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[CheckpointV2DTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val getFlowCheckpointsEndpointV2
  : PublicEndpoint[(Long, Option[Int], Option[Long], Option[String]), ErrorResponse, PaginatedResponse[CheckpointWithPartitioningDTO], Any] = {
    apiV2.get
      .in(V2Paths.Flows / path[Long]("flowId") / V2Paths.Checkpoints)
      .in(query[Option[Int]]("limit").default(Some(10)).validateOption(Validator.inRange(1, 1000)))
      .in(query[Option[Long]]("offset").default(Some(0L)).validateOption(Validator.min(0L)))
      .in(query[Option[String]]("checkpoint-name"))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[CheckpointWithPartitioningDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val getPartitioningByIdEndpointV2
    : PublicEndpoint[Long, ErrorResponse, SingleSuccessResponse[PartitioningWithIdDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId"))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[PartitioningWithIdDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val getPartitioningMeasuresEndpointV2
    : PublicEndpoint[Long, ErrorResponse, MultiSuccessResponse[MeasureDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Measures)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[MultiSuccessResponse[MeasureDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val getFlowPartitioningsEndpointV2
  : PublicEndpoint[(Long, Option[Int], Option[Long]), ErrorResponse, PaginatedResponse[
    PartitioningWithIdDTO
  ], Any] = {
    apiV2.get
      .in(V2Paths.Flows / path[Long]("flowId") / V2Paths.Partitionings)
      .in(query[Option[Int]]("limit").default(Some(10)).validateOption(Validator.inRange(1, 1000)))
      .in(query[Option[Long]]("offset").default(Some(0L)).validateOption(Validator.min(0L)))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[PartitioningWithIdDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val getPartitioningMainFlowEndpointV2
  : PublicEndpoint[Long, ErrorResponse, SingleSuccessResponse[FlowDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.MainFlow)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[FlowDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
      .errorOutVariantPrepend(errorInDataOneOfVariant)
  }

  protected val patchPartitioningParentEndpointV2
  : PublicEndpoint[(Long, PartitioningParentPatchDTO), ErrorResponse, SingleSuccessResponse[PartitioningParentPatchDTO], Any] = {
    apiV2.patch
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Ancestors)
      .in(jsonBody[PartitioningParentPatchDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[PartitioningParentPatchDTO]])
      .errorOutVariantPrepend(conflictErrorOneOfVariant)
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  protected val zioMetricsEndpoint: PublicEndpoint[Unit, Unit, String, Any] = {
    endpoint.get.in(ZioMetrics).out(stringBody)
  }

  protected val healthEndpoint: PublicEndpoint[Unit, Unit, StatusResponse, Any] =
    endpoint.get.in(Health).out(jsonBody[StatusResponse].example(StatusResponse.up))

}

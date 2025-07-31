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

import sttp.model.StatusCode
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.ztapir._
import sttp.tapir.{PublicEndpoint, Validator}
import za.co.absa.atum.model.ApiPaths._
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.ErrorResponse
import za.co.absa.atum.model.envelopes.SuccessResponse._
import za.co.absa.atum.server.api.v2.controller.{CheckpointController, FlowController, PartitioningController}
import za.co.absa.atum.server.api.common.http.{BaseEndpoints, HttpEnv}

import java.util.UUID

object Endpoints extends BaseEndpoints {

  val postCheckpointEndpoint
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

  val postPartitioningEndpoint: PublicEndpoint[
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

  val getPartitioningAdditionalDataEndpoint
    : PublicEndpoint[Long, ErrorResponse, SingleSuccessResponse[AdditionalDataDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.AdditionalData)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[AdditionalDataDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val patchPartitioningAdditionalDataEndpoint
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

  val getPartitioningEndpoint: PublicEndpoint[String, ErrorResponse, SingleSuccessResponse[
    PartitioningWithIdDTO
  ], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings)
      .in(query[String]("partitioning").description("base64 encoded json representation of partitioning"))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[PartitioningWithIdDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getPartitioningCheckpointEndpoint
    : PublicEndpoint[(Long, UUID), ErrorResponse, SingleSuccessResponse[CheckpointV2DTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Checkpoints / path[UUID]("checkpointId"))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[CheckpointV2DTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getPartitioningCheckpointsEndpoint
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

  val getFlowCheckpointsEndpoint
    : PublicEndpoint[(Long, Option[Int], Option[Long], Option[String]), ErrorResponse, PaginatedResponse[
      CheckpointWithPartitioningDTO
    ], Any] = {
    apiV2.get
      .in(V2Paths.Flows / path[Long]("flowId") / V2Paths.Checkpoints)
      .in(query[Option[Int]]("limit").default(Some(10)).validateOption(Validator.inRange(1, 1000)))
      .in(query[Option[Long]]("offset").default(Some(0L)).validateOption(Validator.min(0L)))
      .in(query[Option[String]]("checkpoint-name"))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[CheckpointWithPartitioningDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getPartitioningByIdEndpoint
    : PublicEndpoint[Long, ErrorResponse, SingleSuccessResponse[PartitioningWithIdDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId"))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[PartitioningWithIdDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getPartitioningMeasuresEndpoint
    : PublicEndpoint[Long, ErrorResponse, MultiSuccessResponse[MeasureDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Measures)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[MultiSuccessResponse[MeasureDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getFlowPartitioningsEndpoint
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

  val getPartitioningMainFlowEndpoint
    : PublicEndpoint[Long, ErrorResponse, SingleSuccessResponse[FlowDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.MainFlow)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[FlowDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
      .errorOutVariantPrepend(errorInDataOneOfVariant)
  }

  val patchPartitioningParentEndpoint
  : PublicEndpoint[(Long, PartitioningParentPatchDTO), ErrorResponse, Unit, Any] = {
    apiV2.patch
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Ancestors)
      .in(jsonBody[PartitioningParentPatchDTO])
      .out(statusCode(StatusCode.NoContent))
      .errorOutVariantPrepend(conflictErrorOneOfVariant)
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getPartitioningAncestorsEndpoint
  : PublicEndpoint[(Long, Option[Int], Option[Long]), ErrorResponse, PaginatedResponse[
    PartitioningWithIdDTO
  ], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Ancestors)
      .in(query[Option[Int]]("limit").default(Some(10)).validateOption(Validator.inRange(1, 1000)))
      .in(query[Option[Long]]("offset").default(Some(0L)).validateOption(Validator.min(0L)))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[PartitioningWithIdDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val serverEndpoints: List[ZServerEndpoint[HttpEnv.Env, Any]] = List(
    createServerEndpoint[
      (Long, CheckpointV2DTO),
      ErrorResponse,
      (SingleSuccessResponse[CheckpointV2DTO], String)
    ](
      postCheckpointEndpoint,
      { case (partitioningId: Long, checkpointV2DTO: CheckpointV2DTO) =>
        CheckpointController.postCheckpoint(partitioningId, checkpointV2DTO)
      }
    ),
    createServerEndpoint(postPartitioningEndpoint, PartitioningController.postPartitioning),
    createServerEndpoint(
      getPartitioningAdditionalDataEndpoint,
      PartitioningController.getPartitioningAdditionalData
    ),
    createServerEndpoint[
      (Long, AdditionalDataPatchDTO),
      ErrorResponse,
      SingleSuccessResponse[AdditionalDataDTO]
    ](
      patchPartitioningAdditionalDataEndpoint,
      { case (partitioningId: Long, additionalDataPatchDTO: AdditionalDataPatchDTO) =>
        PartitioningController.patchPartitioningAdditionalData(partitioningId, additionalDataPatchDTO)
      }
    ),
    createServerEndpoint(getPartitioningEndpoint, PartitioningController.getPartitioning),
    createServerEndpoint[
      (Long, UUID),
      ErrorResponse,
      SingleSuccessResponse[CheckpointV2DTO]
    ](
      getPartitioningCheckpointEndpoint,
      { case (partitioningId: Long, checkpointId: UUID) =>
        CheckpointController.getPartitioningCheckpoint(partitioningId, checkpointId)
      }
    ),
    createServerEndpoint[
      (Long, Option[Int], Option[Long], Option[String]),
      ErrorResponse,
      PaginatedResponse[CheckpointV2DTO]
    ](
      getPartitioningCheckpointsEndpoint,
      { case (partitioningId: Long, limit: Option[Int], offset: Option[Long], checkpointName: Option[String]) =>
        CheckpointController.getPartitioningCheckpoints(partitioningId, limit, offset, checkpointName)
      }
    ),
    createServerEndpoint[
      (Long, Option[Int], Option[Long], Option[String]),
      ErrorResponse,
      PaginatedResponse[CheckpointWithPartitioningDTO]
    ](
      getFlowCheckpointsEndpoint,
      { case (flowId: Long, limit: Option[Int], offset: Option[Long], checkpointName: Option[String]) =>
        FlowController.getFlowCheckpoints(flowId, limit, offset, checkpointName)
      }
    ),
    createServerEndpoint(getPartitioningByIdEndpoint, PartitioningController.getPartitioningById),
    createServerEndpoint(getPartitioningMeasuresEndpoint, PartitioningController.getPartitioningMeasures),
    createServerEndpoint(getPartitioningMainFlowEndpoint, PartitioningController.getPartitioningMainFlow),
    createServerEndpoint[
      (Long, Option[Int], Option[Long]),
      ErrorResponse,
      PaginatedResponse[PartitioningWithIdDTO]
    ](
      getFlowPartitioningsEndpoint,
      { case (flowId: Long, limit: Option[Int], offset: Option[Long]) =>
        PartitioningController.getFlowPartitionings(flowId, limit, offset)
      }
    ),
    createServerEndpoint[
      (Long, PartitioningParentPatchDTO),
      ErrorResponse,
      Unit
    ](
      patchPartitioningParentEndpoint,
      { case (partitioningId: Long, partitioningParentPatchDTO: PartitioningParentPatchDTO) =>
        PartitioningController.patchPartitioningParent(partitioningId, partitioningParentPatchDTO)
      }
    ),
    ),
    createServerEndpoint[
      (Long, Option[Int], Option[Long]),
      ErrorResponse,
      PaginatedResponse[PartitioningWithIdDTO]
    ](
      getPartitioningAncestorsEndpoint,
      { case (partitioningId: Long, limit: Option[Int], offset: Option[Long]) =>
        PartitioningController.getPartitioningAncestors(partitioningId, limit, offset)
      }
    )
  )

}

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
import sttp.tapir.{
  Codec,
  CodecFormat,
  DecodeResult,
  EndpointInput,
  PublicEndpoint,
  Schema,
  ValidationResult,
  Validator
}
import za.co.absa.atum.model.ApiPaths._
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.ErrorResponse
import za.co.absa.atum.model.envelopes.SuccessResponse._
import za.co.absa.atum.model.utils.JsonSyntaxExtensions._
import za.co.absa.atum.server.api.v2.controller.{
  CheckpointController,
  FlowController,
  PartitioningController,
  PartitioningControllerImpl
}
import za.co.absa.atum.server.api.common.http.{BaseEndpoints, HttpEnv}

import java.time.ZonedDateTime
import java.util.UUID
import scala.util.{Failure, Success, Try}

object Endpoints extends BaseEndpoints {

  // Checkpoint properties are supplied as a single query parameter carrying a base64url-encoded JSON object,
  // e.g. `checkpoint-properties=eyJleGVjdXRpb25JRCI6Ii4uLiJ9`. Malformed base64 or JSON, or a property without any
  // accepted value (e.g. `{"executionID": []}`, which would match no checkpoint), results in a 400 Bad Request.
  private implicit val checkpointPropertiesQueryCodec: Codec[String, Map[String, Seq[String]], CodecFormat.TextPlain] =
    Codec.string.mapDecode { encoded =>
      Try(encoded.fromBase64As[Map[String, Seq[String]]]) match {
        case Success(Right(properties)) => DecodeResult.Value(properties)
        case _ =>
          Try(encoded.fromBase64As[Map[String, String]]) match {
            case Success(Right(properties)) => DecodeResult.Value(properties.map { case (k, v) => k -> Seq(v) })
            case Success(Left(error)) => DecodeResult.Error(encoded, error)
            case Failure(exception) => DecodeResult.Error(encoded, exception)
          }
      }
    }(_.asBase64EncodedJsonString)
      .validate(Validator.custom { properties =>
        val propertiesWithoutValues = properties.filter(_._2.isEmpty).keys
        if (propertiesWithoutValues.isEmpty) ValidationResult.Valid
        else
          ValidationResult.Invalid(
            s"Checkpoint properties without any accepted value: ${propertiesWithoutValues.mkString(", ")}"
          )
      })

  // Optional half-open window `from <= process start time < to` for checkpoint queries, supplied as ISO-8601 date-times
  // with an offset, e.g. `from=2026-06-01T00:00:00Z`. A malformed value, or `from` not before `to`, results in a
  // 400 Bad Request.
  private val checkpointTimeWindowInput: EndpointInput[(Option[ZonedDateTime], Option[ZonedDateTime])] =
    query[Option[ZonedDateTime]]("from")
      .description("inclusive lower bound of the checkpoint's process start time (ISO-8601 date-time with offset)")
      .and(
        query[Option[ZonedDateTime]]("to")
          .description("exclusive upper bound of the checkpoint's process start time (ISO-8601 date-time with offset)")
      )
      .validate(Validator.custom {
        case (Some(from), Some(to)) if !from.isBefore(to) => ValidationResult.Invalid("'from' must be before 'to'")
        case _ => ValidationResult.Valid
      })

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

  // Single source of truth for the cap is the controller; this is documented in the OpenAPI texts below.
  private val MaxParentPartitionings: Int = PartitioningControllerImpl.MaxParents

  // Tapir's derivation ignores Scala default parameters, so the model DTO's `copyMeasureDefinitions = false` would be
  // documented as a REQUIRED property. This documentation-only override marks it optional with default `false`;
  // runtime decoding stays the Circe decoder from the model module. `partitioning` is a Seq, which Tapir documents as
  // optional by default, but the decoder rejects a body without it, so it is documented as required.
  // The 100-distinct-parents cap is documented in text only: a Tapir `maxSize` validator would be enforced by the
  // interpreter and answer with Tapir's default decode-failure body instead of the `BadRequestResponse` envelope the
  // controller returns (the controller is the single place that enforces the cap, on DISTINCT ids).
  // Likewise the legacy single-parent key `parentPartitioningId` accepted by the decoder cannot be added to the
  // derived schema as a real property, so it is described in the text of `parentPartitioningIds`.
  // Private on purpose: only `jsonBody` of this object needs it, so it cannot leak through `import Endpoints._`.
  private implicit lazy val partitioningSubmitV2Schema: Schema[PartitioningSubmitV2DTO] =
    Schema
      .derived[PartitioningSubmitV2DTO]
      .modify(_.partitioning)(_.copy(isOptional = false))
      .modify(_.parentPartitioningIds)(
        _.description(
          s"Parent partitioning ids (0..N); duplicates are ignored, at most $MaxParentPartitionings distinct ids " +
            "(more is answered with 400). The legacy single-parent key `parentPartitioningId` (number or numeric " +
            "string) is also accepted and is merged into this list."
        )
      )
      .modify(_.copyMeasureDefinitions)(
        _.default(false)
          .description(
            "Copy measure definitions from the parent(s) to a newly created partitioning; " +
              "default false (absent/null = false)"
          )
      )

  val postPartitioningEndpoint: PublicEndpoint[
    PartitioningSubmitV2DTO,
    ErrorResponse,
    (StatusCode, SingleSuccessResponse[PartitioningWithIdDTO], String),
    Any
  ] = {
    apiV2.post
      .in(V2Paths.Partitionings)
      .in(jsonBody[PartitioningSubmitV2DTO])
      .description(
        "Idempotent create-or-link of a partitioning: 201 when it was created, 200 when it already existed " +
          "(parents, if any, are linked in both cases). At most " +
          s"$MaxParentPartitionings distinct parent ids are accepted (400 otherwise); 404 for an unknown parent; " +
          "409 only for a dependency cycle. The legacy single-parent key `parentPartitioningId` is accepted " +
          "besides `parentPartitioningIds`. The `Location` header is returned on 200 as well as on 201."
      )
      .out(
        statusCode
          .description(StatusCode.Created, "Partitioning created; parents (if any) linked")
          .description(
            StatusCode.Ok,
            "Partitioning already existed; parents (if any) linked; body carries the stored author"
          )
      )
      .out(jsonBody[SingleSuccessResponse[PartitioningWithIdDTO]])
      .out(header[String]("Location"))
      .errorOutVariantPrepend(conflictErrorOneOfVariant)
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getPartitioningAdditionalDataEndpoint
    : PublicEndpoint[Long, ErrorResponse, MultiSuccessResponse[AdditionalDataItemV2DTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.AdditionalData)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[MultiSuccessResponse[AdditionalDataItemV2DTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val patchPartitioningAdditionalDataEndpoint
    : PublicEndpoint[(Long, AdditionalDataPatchDTO), ErrorResponse, MultiSuccessResponse[AdditionalDataItemV2DTO], Any] = {
    apiV2.patch
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.AdditionalData)
      .in(jsonBody[AdditionalDataPatchDTO])
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[MultiSuccessResponse[AdditionalDataItemV2DTO]])
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
    : PublicEndpoint[(Long, UUID, Boolean), ErrorResponse, SingleSuccessResponse[CheckpointV2DTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Checkpoints / path[UUID]("checkpointId"))
      .in(query[Boolean]("include-properties").default(false))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[CheckpointV2DTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getPartitioningCheckpointsEndpoint
    : PublicEndpoint[(Long, Int, Long, Option[String], Option[Map[String, Seq[String]]], Option[Boolean], Option[ZonedDateTime], Option[ZonedDateTime], Boolean), ErrorResponse, PaginatedResponse[
      CheckpointV2DTO
    ], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Checkpoints)
      // Tapir's .default works with non-optional types only
      .in(query[Int]("limit").default(10).validate(Validator.inRange(1, 1000)))
      .in(query[Long]("offset").default(0L).validate(Validator.min(0L)))
      .in(query[Option[String]]("checkpoint-name"))
      .in(query[Option[Map[String, Seq[String]]]]("checkpoint-properties"))
      .in(query[Option[Boolean]]("latest-first"))
      .in(checkpointTimeWindowInput)
      .in(query[Boolean]("include-properties").default(false))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[CheckpointV2DTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getFlowCheckpointsEndpoint
    : PublicEndpoint[(Long, Int, Long, Option[String], Option[Map[String, Seq[String]]], Option[Boolean], Option[ZonedDateTime], Option[ZonedDateTime], Boolean), ErrorResponse, PaginatedResponse[
      CheckpointWithPartitioningDTO
    ], Any] = {
    apiV2.get
      .in(V2Paths.Flows / path[Long]("flowId") / V2Paths.Checkpoints)
      .in(query[Int]("limit").default(10).validate(Validator.inRange(1, 1000)))
      .in(query[Long]("offset").default(0L).validate(Validator.min(0L)))
      .in(query[Option[String]]("checkpoint-name"))
      .in(query[Option[Map[String, Seq[String]]]]("checkpoint-properties"))
      .in(query[Option[Boolean]]("latest-first"))
      .in(checkpointTimeWindowInput)
      .in(query[Boolean]("include-properties").default(false))
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
    : PublicEndpoint[(Long, Int, Long), ErrorResponse, PaginatedResponse[
      PartitioningWithIdDTO
    ], Any] = {
    apiV2.get
      .in(V2Paths.Flows / path[Long]("flowId") / V2Paths.Partitionings)
      .in(query[Int]("limit").default(10).validate(Validator.inRange(1, 1000)))
      .in(query[Long]("offset").default(0L).validate(Validator.min(0L)))
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
  : PublicEndpoint[(Long, Int, Long), ErrorResponse, PaginatedResponse[
    PartitioningWithIdDTO
  ], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Ancestors)
      .in(query[Int]("limit").default(10).validate(Validator.inRange(1, 1000)))
      .in(query[Long]("offset").default(0L).validate(Validator.min(0L)))
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
    createServerEndpoint[Long, ErrorResponse, MultiSuccessResponse[AdditionalDataItemV2DTO]](
      getPartitioningAdditionalDataEndpoint,
      PartitioningController.getPartitioningAdditionalData
    ),
    createServerEndpoint[
      (Long, AdditionalDataPatchDTO),
      ErrorResponse,
      MultiSuccessResponse[AdditionalDataItemV2DTO]
    ](
      patchPartitioningAdditionalDataEndpoint,
      { case (partitioningId: Long, additionalDataPatchDTO: AdditionalDataPatchDTO) =>
        PartitioningController.patchPartitioningAdditionalData(partitioningId, additionalDataPatchDTO)
      }
    ),
    createServerEndpoint(getPartitioningEndpoint, PartitioningController.getPartitioning),
    createServerEndpoint[
      (Long, UUID, Boolean),
      ErrorResponse,
      SingleSuccessResponse[CheckpointV2DTO]
    ](
      getPartitioningCheckpointEndpoint,
      { case (partitioningId: Long, checkpointId: UUID, includeProperties: Boolean) =>
        CheckpointController.getPartitioningCheckpoint(partitioningId, checkpointId, includeProperties)
      }
    ),
    createServerEndpoint[
      (Long, Int, Long, Option[String], Option[Map[String, Seq[String]]], Option[Boolean], Option[ZonedDateTime], Option[ZonedDateTime], Boolean),
      ErrorResponse,
      PaginatedResponse[CheckpointV2DTO]
    ](
      getPartitioningCheckpointsEndpoint,
      { case (partitioningId: Long, limit: Int, offset: Long, checkpointName: Option[String], checkpointProperties: Option[Map[String, Seq[String]]], latestFirst: Option[Boolean], from: Option[ZonedDateTime], to: Option[ZonedDateTime], includeProperties: Boolean) =>
        CheckpointController.getPartitioningCheckpoints(partitioningId, limit, offset, checkpointName, checkpointProperties, latestFirst, from, to, includeProperties)
      }
    ),
    createServerEndpoint[
      (Long, Int, Long, Option[String], Option[Map[String, Seq[String]]], Option[Boolean], Option[ZonedDateTime], Option[ZonedDateTime], Boolean),
      ErrorResponse,
      PaginatedResponse[CheckpointWithPartitioningDTO]
    ](
      getFlowCheckpointsEndpoint,
      { case (flowId: Long, limit: Int, offset: Long, checkpointName: Option[String], checkpointProperties: Option[Map[String, Seq[String]]], latestFirst: Option[Boolean], from: Option[ZonedDateTime], to: Option[ZonedDateTime], includeProperties: Boolean) =>
        FlowController.getFlowCheckpoints(flowId, limit, offset, checkpointName, checkpointProperties, latestFirst, from, to, includeProperties)
      }
    ),
    createServerEndpoint(getPartitioningByIdEndpoint, PartitioningController.getPartitioningById),
    createServerEndpoint(getPartitioningMeasuresEndpoint, PartitioningController.getPartitioningMeasures),
    createServerEndpoint(getPartitioningMainFlowEndpoint, PartitioningController.getPartitioningMainFlow),
    createServerEndpoint[
      (Long, Int, Long),
      ErrorResponse,
      PaginatedResponse[PartitioningWithIdDTO]
    ](
      getFlowPartitioningsEndpoint,
      { case (flowId: Long, limit: Int, offset: Long) =>
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
    createServerEndpoint[
      (Long, Int, Long),
      ErrorResponse,
      PaginatedResponse[PartitioningWithIdDTO]
    ](
      getPartitioningAncestorsEndpoint,
      { case (partitioningId: Long, limit: Int, offset: Long) =>
        PartitioningController.getPartitioningAncestors(partitioningId, limit, offset)
      }
    )
  )

}

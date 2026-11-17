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
import sttp.tapir.{Codec, CodecFormat, DecodeResult, EndpointInput, PublicEndpoint, ValidationResult, Validator}
import za.co.absa.atum.model.ApiPaths._
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.ErrorResponse
import za.co.absa.atum.model.envelopes.SuccessResponse._
import za.co.absa.atum.model.utils.JsonSyntaxExtensions._
import za.co.absa.atum.server.api.v2.controller.{CheckpointController, FlowController, PartitioningController}
import za.co.absa.atum.server.api.common.http.{BaseEndpoints, HttpEnv}

import java.time.{Duration, ZonedDateTime}
import java.util.UUID
import scala.util.{Failure, Success, Try}

object Endpoints extends BaseEndpoints {

  private implicit val partitioningKeysQueryCodec: Codec[String, Map[String, String], CodecFormat.TextPlain] =
    Codec.string.mapDecode { encoded =>
      Try(encoded.fromBase64As[Map[String, String]]) match {
        case Success(Right(keys)) => DecodeResult.Value(keys)
        case Success(Left(error)) => DecodeResult.Error(encoded, error)
        case Failure(exception) => DecodeResult.Error(encoded, exception)
      }
    }(_.asBase64EncodedJsonString)
      .validate(Validator.custom { keys =>
        if (keys.exists { case (key, value) => key.contains('\u0000') || value.contains('\u0000') })
          ValidationResult.Invalid("Partitioning keys and values must not contain NUL characters")
        else ValidationResult.Valid
      })

  // Checkpoint properties are supplied as a single query parameter carrying a base64url-encoded JSON object,
  // e.g. `checkpoint-properties=eyJleGVjdXRpb25JRCI6Ii4uLiJ9`. Malformed base64 or JSON, or a property without any
  // accepted value (e.g. `{"executionID": []}`, which would match no checkpoint), results in a 400 Bad Request.
  private implicit val checkpointPropertiesQueryCodec: Codec[String, Map[String, Seq[String]], CodecFormat.TextPlain] =
    Codec.string
      .mapDecode { encoded =>
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

  private val maxPartitioningSearchWindow = Duration.ofDays(365)

  private val partitioningSearchInput
    : EndpointInput[(Option[Map[String, String]], Option[ZonedDateTime], Option[ZonedDateTime])] =
    query[Option[Map[String, String]]]("keys")
      .description("base64url-encoded JSON object of partial partitioning keys; all supplied string values must match")
      .and(checkpointTimeWindowInput)
      .validate(Validator.custom {
        case (_, Some(_), None) | (_, None, Some(_)) =>
          ValidationResult.Invalid("'from' and 'to' must be supplied together")
        case (_, Some(from), Some(to)) if Duration.between(from.toInstant, to.toInstant)
          .compareTo(maxPartitioningSearchWindow) > 0 =>
          ValidationResult.Invalid("The time window must not exceed 365 days")
        case (keys, None, None) if keys.forall(_.isEmpty) =>
          ValidationResult.Invalid("At least one non-empty key filter or complete time window is required")
        case _ => ValidationResult.Valid
      })

  private val paginationInput: EndpointInput[(Int, Long)] =
    query[Int]("limit")
      .default(10)
      .validate(Validator.inRange(1, 1000))
      .and(
        query[Long]("offset")
          .default(0L)
          .validate(Validator.min(0L))
      )

  private val DefaultLineageDirection = "both"
  private val DefaultLineageDepth = 5
  private val MinLineageDepth = 1
  private val MaxLineageDepth = 20
  private val AllowedLineageDirections = List("both", "downstream", "upstream")

  // Normalizing codec so case-insensitive / padded direction inputs (e.g. "  UPSTREAM ") pass validation while
  // still advertising the allowed lowercase enum values in OpenAPI.
  private val lineageDirectionCodec: Codec[List[String], String, CodecFormat.TextPlain] =
    Codec.listHead(
      Codec.string
        .mapDecode(s => DecodeResult.Value(s.trim.toLowerCase))(identity)
        .validate(Validator.enumeration(AllowedLineageDirections))
    )

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
    (StatusCode, SingleSuccessResponse[PartitioningWithIdDTO], String),
    Any
  ] = {
    apiV2.post
      .in(V2Paths.Partitionings)
      .in(jsonBody[PartitioningSubmitV2DTO])
      .out(
        statusCode
          .description(StatusCode.Created, "Partitioning created (and linked to any specified parents)")
          .description(StatusCode.Ok, "Partitioning already existed (and linked to any new parents)")
      )
      .out(jsonBody[SingleSuccessResponse[PartitioningWithIdDTO]])
      .out(header[String]("Location"))
      .errorOutVariantPrepend(conflictErrorOneOfVariant)
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
      .summary("Create or obtain a partitioning")
      .description(
        "Creates a new partitioning (201 Created) or returns the existing one (200 OK), optionally linking " +
          "0..100 distinct parent partitionings. Returns 400 Bad Request on invalid payload or >100 distinct " +
          "parents, 404 Not Found if a parent does not exist, and 409 Conflict if linking would create a cycle " +
          "or add a parent to an existing partitioning that already has descendants."
      )
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
    : PublicEndpoint[(Long, AdditionalDataPatchDTO), ErrorResponse, MultiSuccessResponse[
      AdditionalDataItemV2DTO
    ], Any] = {
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

  val getPartitioningCheckpointsEndpoint: PublicEndpoint[
    (
      Long,
      Int,
      Long,
      Option[String],
      Option[Map[String, Seq[String]]],
      Option[Boolean],
      Option[ZonedDateTime],
      Option[ZonedDateTime],
      Boolean
    ),
    ErrorResponse,
    PaginatedResponse[
      CheckpointV2DTO
    ],
    Any
  ] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Checkpoints)
      .in(paginationInput)
      .in(query[Option[String]]("checkpoint-name"))
      .in(query[Option[Map[String, Seq[String]]]]("checkpoint-properties"))
      .in(query[Option[Boolean]]("latest-first"))
      .in(checkpointTimeWindowInput)
      .in(query[Boolean]("include-properties").default(false))
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[CheckpointV2DTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getFlowCheckpointsEndpoint: PublicEndpoint[
    (
      Long,
      Int,
      Long,
      Option[String],
      Option[Map[String, Seq[String]]],
      Option[Boolean],
      Option[ZonedDateTime],
      Option[ZonedDateTime],
      Boolean
    ),
    ErrorResponse,
    PaginatedResponse[
      CheckpointWithPartitioningDTO
    ],
    Any
  ] = {
    apiV2.get
      .in(V2Paths.Flows / path[Long]("flowId") / V2Paths.Checkpoints)
      .in(paginationInput)
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

  val searchPartitioningsEndpoint
    : PublicEndpoint[(Int, Long, Option[Map[String, String]], Option[ZonedDateTime], Option[ZonedDateTime]), ErrorResponse, PaginatedResponse[
      PartitioningWithIdDTO
    ], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / V2Paths.Search)
      .in(paginationInput)
      .in(partitioningSearchInput)
      .summary("Search partitionings")
      .description(
        "Search distinct partitionings by partial keys and/or checkpoint process start time, ordered by partitioning ID " +
          "ascending. Key-only searches include partitionings without checkpoints. Time selection requires both bounds, " +
          "a window no longer than 365 days, and at least one matching checkpoint. Pagination is live, not a snapshot."
      )
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[PartitioningWithIdDTO]])
  }

  val getPartitioningMeasuresEndpoint: PublicEndpoint[Long, ErrorResponse, MultiSuccessResponse[MeasureDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Measures)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[MultiSuccessResponse[MeasureDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getFlowPartitioningsEndpoint: PublicEndpoint[(Long, Int, Long), ErrorResponse, PaginatedResponse[
    PartitioningWithIdDTO
  ], Any] = {
    apiV2.get
      .in(V2Paths.Flows / path[Long]("flowId") / V2Paths.Partitionings)
      .in(paginationInput)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[PartitioningWithIdDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getPartitioningMainFlowEndpoint: PublicEndpoint[Long, ErrorResponse, SingleSuccessResponse[FlowDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.MainFlow)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[FlowDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
      .errorOutVariantPrepend(errorInDataOneOfVariant)
  }

  val patchPartitioningParentEndpoint: PublicEndpoint[(Long, PartitioningParentPatchDTO), ErrorResponse, Unit, Any] = {
    apiV2.patch
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Ancestors)
      .in(jsonBody[PartitioningParentPatchDTO])
      .out(statusCode(StatusCode.NoContent))
      .errorOutVariantPrepend(conflictErrorOneOfVariant)
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
      .summary("Link a parent partitioning")
      .description(
        "Links an existing parent partitioning to the given child partitioning (204 No Content). " +
          "Returns 400 Bad Request on invalid payload, 404 Not Found if either partitioning does not exist, " +
          "and 409 Conflict if the link would create a cycle or the child already has descendants."
      )
  }

  val getPartitioningAncestorsEndpoint: PublicEndpoint[(Long, Int, Long), ErrorResponse, PaginatedResponse[
    PartitioningWithIdDTO
  ], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Ancestors)
      .in(paginationInput)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[PartitioningWithIdDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
  }

  val getPartitioningDescendantsEndpoint: PublicEndpoint[(Long, Int, Long), ErrorResponse, PaginatedResponse[
    PartitioningWithIdDTO
  ], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Descendants)
      .in(paginationInput)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[PaginatedResponse[PartitioningWithIdDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
      .summary("List descendant partitionings")
      .description(
        "Returns a paginated list of all transitive descendants of the partitioning (200 OK). " +
          "Returns 400 Bad Request for invalid pagination parameters and 404 Not Found if the partitioning does not exist."
      )
  }

  val getPartitioningParentsEndpoint
    : PublicEndpoint[Long, ErrorResponse, MultiSuccessResponse[ParentPartitioningDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Parents)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[MultiSuccessResponse[ParentPartitioningDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
      .summary("List direct parent partitionings")
      .description(
        "Returns all immediate parents of the partitioning with edge metadata (200 OK). " +
          "Returns 404 Not Found if the partitioning does not exist."
      )
  }

  val getPartitioningChildrenEndpoint
    : PublicEndpoint[Long, ErrorResponse, MultiSuccessResponse[ChildPartitioningDTO], Any] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Children)
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[MultiSuccessResponse[ChildPartitioningDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
      .summary("List direct child partitionings")
      .description(
        "Returns all immediate children of the partitioning with edge metadata (200 OK). " +
          "Returns 404 Not Found if the partitioning does not exist."
      )
  }

  val getPartitioningLineageGraphEndpoint: PublicEndpoint[
    (Long, String, Int),
    ErrorResponse,
    SingleSuccessResponse[LineageGraphDTO],
    Any
  ] = {
    apiV2.get
      .in(V2Paths.Partitionings / path[Long]("partitioningId") / V2Paths.Lineage)
      .in(
        query[String]("direction")(lineageDirectionCodec)
          .default(DefaultLineageDirection)
          .description(s"Traversal direction: 'both', 'upstream', or 'downstream' (default: '$DefaultLineageDirection')")
      )
      .in(
        query[Int]("depth")
          .default(DefaultLineageDepth)
          .validate(Validator.inRange(MinLineageDepth, MaxLineageDepth))
          .description(
            s"Maximum traversal depth (default: $DefaultLineageDepth, " +
              s"range: $MinLineageDepth..$MaxLineageDepth)"
          )
      )
      .out(statusCode(StatusCode.Ok))
      .out(jsonBody[SingleSuccessResponse[LineageGraphDTO]])
      .errorOutVariantPrepend(notFoundErrorOneOfVariant)
      .summary("Get partitioning lineage sub-graph")
      .description(
        "Returns the lineage sub-graph (nodes and direct edges) centered on the given partitioning (200 OK). " +
          "Returns 400 Bad Request if 'direction' or 'depth' is invalid, and 404 Not Found if the partitioning does not exist."
      )
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
      (
        Long,
        Int,
        Long,
        Option[String],
        Option[Map[String, Seq[String]]],
        Option[Boolean],
        Option[ZonedDateTime],
        Option[ZonedDateTime],
        Boolean
      ),
      ErrorResponse,
      PaginatedResponse[CheckpointV2DTO]
    ](
      getPartitioningCheckpointsEndpoint,
      {
        case (
              partitioningId: Long,
              limit: Int,
              offset: Long,
              checkpointName: Option[String],
              checkpointProperties: Option[Map[String, Seq[String]]],
              latestFirst: Option[Boolean],
              from: Option[ZonedDateTime],
              to: Option[ZonedDateTime],
              includeProperties: Boolean
            ) =>
          CheckpointController.getPartitioningCheckpoints(
            partitioningId,
            limit,
            offset,
            checkpointName,
            checkpointProperties,
            latestFirst,
            from,
            to,
            includeProperties
          )
      }
    ),
    createServerEndpoint[
      (
        Long,
        Int,
        Long,
        Option[String],
        Option[Map[String, Seq[String]]],
        Option[Boolean],
        Option[ZonedDateTime],
        Option[ZonedDateTime],
        Boolean
      ),
      ErrorResponse,
      PaginatedResponse[CheckpointWithPartitioningDTO]
    ](
      getFlowCheckpointsEndpoint,
      {
        case (
              flowId: Long,
              limit: Int,
              offset: Long,
              checkpointName: Option[String],
              checkpointProperties: Option[Map[String, Seq[String]]],
              latestFirst: Option[Boolean],
              from: Option[ZonedDateTime],
              to: Option[ZonedDateTime],
              includeProperties: Boolean
            ) =>
          FlowController.getFlowCheckpoints(
            flowId,
            limit,
            offset,
            checkpointName,
            checkpointProperties,
            latestFirst,
            from,
            to,
            includeProperties
          )
      }
    ),
    createServerEndpoint[
      (Int, Long, Option[Map[String, String]], Option[ZonedDateTime], Option[ZonedDateTime]),
      ErrorResponse,
      PaginatedResponse[PartitioningWithIdDTO]
    ](
      searchPartitioningsEndpoint,
      { case (limit: Int, offset: Long, keys: Option[Map[String, String]], from: Option[ZonedDateTime], to: Option[ZonedDateTime]) =>
        PartitioningController.searchPartitionings(limit, offset, keys, from, to)
      }
    ),
    createServerEndpoint(getPartitioningByIdEndpoint, PartitioningController.getPartitioningById),
    createServerEndpoint(getPartitioningMeasuresEndpoint, PartitioningController.getPartitioningMeasures),
    createServerEndpoint(getPartitioningMainFlowEndpoint, PartitioningController.getPartitioningMainFlow),
    createServerEndpoint(getFlowPartitioningsEndpoint, (PartitioningController.getFlowPartitionings _).tupled),
    createServerEndpoint(patchPartitioningParentEndpoint, (PartitioningController.patchPartitioningParent _).tupled),
    createServerEndpoint(getPartitioningAncestorsEndpoint, (PartitioningController.getPartitioningAncestors _).tupled),
    createServerEndpoint(
      getPartitioningDescendantsEndpoint,
      (PartitioningController.getPartitioningDescendants _).tupled
    ),
    createServerEndpoint(getPartitioningParentsEndpoint, PartitioningController.getPartitioningParents),
    createServerEndpoint(getPartitioningChildrenEndpoint, PartitioningController.getPartitioningChildren),
    createServerEndpoint(
      getPartitioningLineageGraphEndpoint,
      (PartitioningController.getPartitioningLineageGraph _).tupled
    )
  )

}

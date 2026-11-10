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

package za.co.absa.atum.server.api.v2.controller

import za.co.absa.atum.model.ApiPaths.V2Paths
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.SuccessResponse._
import za.co.absa.atum.model.envelopes.{BadRequestResponse, ErrorResponse, GeneralErrorResponse}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonDeserializationSyntax
import za.co.absa.atum.server.api.common.controller.BaseController
import za.co.absa.atum.server.api.v2.service.PartitioningService
import za.co.absa.atum.server.model.CreationResult.{Created, Existing}
import za.co.absa.atum.server.model.{CreationResult, PaginatedResult}
import sttp.model.StatusCode
import zio._

class PartitioningControllerImpl(partitioningService: PartitioningService)
    extends PartitioningController
    with BaseController {

  override def getPartitioningAdditionalData(
    partitioningId: Long
  ): IO[ErrorResponse, MultiSuccessResponse[AdditionalDataItemV2DTO]] = {
    mapToMultiSuccessResponse(
      serviceCall[Seq[AdditionalDataItemV2DTO], Seq[AdditionalDataItemV2DTO]](
        partitioningService.getPartitioningAdditionalData(partitioningId)
      )
    )
  }

  override def getPartitioningById(
    partitioningId: Long
  ): IO[ErrorResponse, SingleSuccessResponse[PartitioningWithIdDTO]] = {
    mapToSingleSuccessResponse(
      serviceCall[PartitioningWithIdDTO, PartitioningWithIdDTO](
        partitioningService.getPartitioningById(partitioningId)
      )
    )
  }

  override def postPartitioning(
    partitioningSubmitDTO: PartitioningSubmitV2DTO
  ): IO[ErrorResponse, (StatusCode, SingleSuccessResponse[PartitioningWithIdDTO], String)] = {
    // dedupe here as well: the cap must apply to DISTINCT ids whoever calls the controller (the decoder and the SQL
    // dedupe too, the SQL one being the authoritative one for what is stored)
    val distinctParentIds = partitioningSubmitDTO.parentPartitioningIds.distinct
    for {
      _ <- ZIO.when(distinctParentIds.size > PartitioningControllerImpl.MaxParents)(
        ZIO.fail(
          BadRequestResponse(
            s"Too many parent partitionings: ${distinctParentIds.size}, " +
              s"maximum allowed is ${PartitioningControllerImpl.MaxParents}"
          )
        )
      )
      result <- serviceCall[CreationResult[PartitioningWithIdDTO], CreationResult[PartitioningWithIdDTO]](
        partitioningService.createPartitioning(partitioningSubmitDTO.copy(parentPartitioningIds = distinctParentIds))
      )
      response = SingleSuccessResponse(result.data)
      status = result match {
        case Created(_) => StatusCode.Created
        case Existing(_) => StatusCode.Ok
      }
      uri <- createRootAnchoredResourcePath(Seq(V2Paths.Partitionings, result.data.id.toString), apiVersion = 2)
    } yield (status, response, uri)
  }

  override def patchPartitioningAdditionalData(
    partitioningId: Long,
    additionalDataPatchDTO: AdditionalDataPatchDTO
  ): IO[ErrorResponse, MultiSuccessResponse[AdditionalDataItemV2DTO]] = {
    mapToMultiSuccessResponse(
      serviceCall[Seq[AdditionalDataItemV2DTO], Seq[AdditionalDataItemV2DTO]](
        partitioningService.patchAdditionalData(partitioningId, additionalDataPatchDTO)
      )
    )
  }

  override def getPartitioningMeasures(
    partitioningId: Long
  ): IO[ErrorResponse, MultiSuccessResponse[MeasureDTO]] = {
    mapToMultiSuccessResponse(
      serviceCall[Seq[MeasureDTO], Seq[MeasureDTO]](
        partitioningService.getPartitioningMeasuresById(partitioningId)
      )
    )
  }

  override def getFlowPartitionings(
    flowId: Long,
    limit: Int,
    offset: Long
  ): IO[ErrorResponse, PaginatedResponse[PartitioningWithIdDTO]] = {
    mapToPaginatedResponse(
      limit,
      offset,
      serviceCall[PaginatedResult[PartitioningWithIdDTO], PaginatedResult[PartitioningWithIdDTO]](
        partitioningService.getFlowPartitionings(flowId, limit, offset)
      )
    )
  }

  override def getPartitioning(
    partitioning: String
  ): IO[ErrorResponse, SingleSuccessResponse[PartitioningWithIdDTO]] = {
    for {
      decodedPartitions <- ZIO
        .fromEither(partitioning.fromBase64As[PartitioningDTO])
        .mapError(error => GeneralErrorResponse(error.getMessage))
      response <-
        mapToSingleSuccessResponse[PartitioningWithIdDTO](
          serviceCall[PartitioningWithIdDTO, PartitioningWithIdDTO](
            partitioningService.getPartitioning(decodedPartitions)
          )
        )
    } yield response
  }

  override def getPartitioningMainFlow(
    partitioningId: Long
  ): IO[ErrorResponse, SingleSuccessResponse[FlowDTO]] = {
    mapToSingleSuccessResponse(
      serviceCall[FlowDTO, FlowDTO](
        partitioningService.getPartitioningMainFlow(partitioningId)
      )
    )
  }

  override def patchPartitioningParent(
    partitioningId: Long,
    partitioningParentPatchDTO: PartitioningParentPatchDTO
  ): IO[ErrorResponse, Unit] = {
    serviceCall[Unit, Unit](
      partitioningService.patchPartitioningParent(partitioningId, partitioningParentPatchDTO),
      _ => ()
    )
  }

  override def getPartitioningAncestors(
    partitioningId: Long,
    limit: Int,
    offset: Long
  ): IO[ErrorResponse, PaginatedResponse[PartitioningWithIdDTO]] = {
    mapToPaginatedResponse(
      limit,
      offset,
      serviceCall[PaginatedResult[PartitioningWithIdDTO], PaginatedResult[PartitioningWithIdDTO]](
        partitioningService.getPartitioningAncestors(partitioningId, limit, offset)
      )
    )
  }

  override def getPartitioningParents(
    partitioningId: Long
  ): IO[ErrorResponse, MultiSuccessResponse[ParentPartitioningDTO]] = {
    mapToMultiSuccessResponse(
      serviceCall[Seq[ParentPartitioningDTO], Seq[ParentPartitioningDTO]](
        partitioningService.getPartitioningParents(partitioningId)
      )
    )
  }

  override def getPartitioningChildren(
    partitioningId: Long
  ): IO[ErrorResponse, MultiSuccessResponse[ChildPartitioningDTO]] = {
    mapToMultiSuccessResponse(
      serviceCall[Seq[ChildPartitioningDTO], Seq[ChildPartitioningDTO]](
        partitioningService.getPartitioningChildren(partitioningId)
      )
    )
  }

  override def postPartitioningParents(
    partitioningId: Long,
    submitDTO: PartitioningParentsSubmitDTO
  ): IO[ErrorResponse, Unit] = {
    val distinctParentIds = submitDTO.parentPartitioningIds.distinct
    for {
      _ <- ZIO.when(submitDTO.author.trim.isEmpty)(
        ZIO.fail(BadRequestResponse("author must not be blank"))
      )
      _ <- ZIO.when(distinctParentIds.isEmpty)(
        ZIO.fail(BadRequestResponse("parentPartitioningIds must not be empty"))
      )
      _ <- ZIO.when(distinctParentIds.size > PartitioningControllerImpl.MaxParents)(
        ZIO.fail(
          BadRequestResponse(
            s"Too many parent partitionings: ${distinctParentIds.size}, " +
              s"maximum allowed is ${PartitioningControllerImpl.MaxParents}"
          )
        )
      )
      _ <- serviceCall[Unit, Unit](
        partitioningService.linkPartitioningToParents(
          partitioningId,
          submitDTO.copy(parentPartitioningIds = distinctParentIds)
        ),
        _ => ()
      )
    } yield ()
  }

  override def getPartitioningLineageGraph(
    partitioningId: Long,
    direction: Option[String],
    depth: Option[Int]
  ): IO[ErrorResponse, SingleSuccessResponse[LineageGraphDTO]] = {
    val dir = direction.map(_.trim.toLowerCase).getOrElse("both")
    val d = depth.getOrElse(5)
    for {
      _ <- ZIO.when(!Set("both", "upstream", "downstream").contains(dir))(
        ZIO.fail(BadRequestResponse(s"Invalid direction '$dir'. Allowed values: 'both', 'upstream', 'downstream'."))
      )
      _ <- ZIO.when(d < 1 || d > 20)(
        ZIO.fail(BadRequestResponse(s"Depth $d out of range. Allowed range is 1 to 20."))
      )
      response <- mapToSingleSuccessResponse(
        serviceCall[LineageGraphDTO, LineageGraphDTO](
          partitioningService.getPartitioningLineageGraph(partitioningId, dir, d)
        )
      )
    } yield response
  }

}

object PartitioningControllerImpl {

  /** Maximum number of DISTINCT parent ids accepted by `POST /partitionings` (more is answered with 400). */
  val MaxParents = 100

  val layer: URLayer[PartitioningService, PartitioningController] = ZLayer {
    for {
      partitioningService <- ZIO.service[PartitioningService]
    } yield new PartitioningControllerImpl(partitioningService)
  }
}

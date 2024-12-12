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

package za.co.absa.atum.server.api.controller

import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.{ErrorResponse, GeneralErrorResponse, InternalServerErrorResponse}
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.http.ApiPaths.V2Paths
import za.co.absa.atum.server.api.service.PartitioningService
import za.co.absa.atum.model.envelopes.SuccessResponse._
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonDeserializationSyntax
import za.co.absa.atum.server.model.PaginatedResult
import zio._

class PartitioningControllerImpl(partitioningService: PartitioningService)
    extends PartitioningController
    with BaseController {

  override def createPartitioningIfNotExistsV1(
    partitioningSubmitDTO: PartitioningSubmitDTO
  ): IO[ErrorResponse, AtumContextDTO] = {
    val atumContextDTOEffect = for {
      _ <- partitioningService
        .createPartitioningIfNotExists(partitioningSubmitDTO)
        .mapError(serviceError => InternalServerErrorResponse(serviceError.message))

      partitioningWithId <- partitioningService
        .getPartitioning(partitioningSubmitDTO.partitioning)
        .mapError(serviceError => InternalServerErrorResponse(serviceError.message))

      measures <- partitioningService
        .getPartitioningMeasures(partitioningSubmitDTO.partitioning)
        .mapError { serviceError: ServiceError =>
          InternalServerErrorResponse(serviceError.message)
        }

      additionalData <- partitioningService
        .getPartitioningAdditionalData(partitioningWithId.id)
        .mapError { serviceError: ServiceError =>
          InternalServerErrorResponse(serviceError.message)
        }

      additionalDataForContext <- ZIO.succeed {
        additionalData.data.map { case (key, value) =>
          key -> value.flatMap(_.value)
        }
      }

    } yield AtumContextDTO(partitioningSubmitDTO.partitioning, measures.toSet, additionalDataForContext)

    atumContextDTOEffect
  }

  override def getPartitioningAdditionalData(
    partitioningId: Long
  ): IO[ErrorResponse, SingleSuccessResponse[AdditionalDataDTO]] = {
    mapToSingleSuccessResponse(
      serviceCall[AdditionalDataDTO, AdditionalDataDTO](
        partitioningService.getPartitioningAdditionalData(partitioningId)
      )
    )
  }
  override def getPartitioningByIdV2(
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
  ): IO[ErrorResponse, (SingleSuccessResponse[PartitioningWithIdDTO], String)] = {
    for {
      response <- mapToSingleSuccessResponse(
        serviceCall[PartitioningWithIdDTO, PartitioningWithIdDTO](
          partitioningService.createPartitioning(partitioningSubmitDTO)
        )
      )
      uri <- createV2RootAnchoredResourcePath(
        Seq(V2Paths.Partitionings, response.data.id.toString)
      )
    } yield (response, uri)
  }

//  override def patchPartitioningParentV2(
//    partitioningId: Long,
//    parentPartitioningID: Long,
//    byUser: String
//  ): IO[ErrorResponse,SingleSuccessResponse[ParentPatchV2DTO]] = {
//    mapToSingleSuccessResponse(
//      serviceCall[ParentPatchV2DTO, ParentPatchV2DTO](
//        partitioningService.patchPartitioningParent(partitioningId, parentPartitioningID, byUser)
//      )
//    )
//  }

  override def patchPartitioningAdditionalDataV2(
    partitioningId: Long,
    additionalDataPatchDTO: AdditionalDataPatchDTO
  ): IO[ErrorResponse, SingleSuccessResponse[AdditionalDataDTO]] = {
    mapToSingleSuccessResponse(
      serviceCall[AdditionalDataDTO, AdditionalDataDTO](
        partitioningService.patchAdditionalData(partitioningId, additionalDataPatchDTO)
      )
    )
  }

  override def getPartitioningMeasuresV2(
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
    limit: Option[Int],
    offset: Option[Long]
  ): IO[ErrorResponse, PaginatedResponse[PartitioningWithIdDTO]] = {
    mapToPaginatedResponse(
      limit.get,
      offset.get,
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

  override def getAncestors(
    partitioningId: Long,
    limit: Option[Int],
    offset: Option[Long]
   ): IO[ErrorResponse, PaginatedResponse[PartitioningWithIdDTO]] = {
    mapToPaginatedResponse(
      limit.get,
      offset.get,
      serviceCall[PaginatedResult[PartitioningWithIdDTO], PaginatedResult[PartitioningWithIdDTO]](
        partitioningService.getAncestors(partitioningId, limit, offset)
      )
    )
  }

}

object PartitioningControllerImpl {
  val layer: URLayer[PartitioningService, PartitioningController] = ZLayer {
    for {
      partitioningService <- ZIO.service[PartitioningService]
    } yield new PartitioningControllerImpl(partitioningService)
  }
}

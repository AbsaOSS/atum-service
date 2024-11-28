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
import za.co.absa.atum.model.envelopes.ErrorResponse
import za.co.absa.atum.model.envelopes.SuccessResponse._
import zio.IO
import zio.macros.accessible

@accessible
trait PartitioningController {
  def createPartitioningIfNotExistsV1(
    partitioningSubmitDTO: PartitioningSubmitDTO
  ): IO[ErrorResponse, AtumContextDTO]

  def postPartitioning(
    partitioningSubmitDTO: PartitioningSubmitV2DTO
  ): IO[ErrorResponse, (SingleSuccessResponse[PartitioningWithIdDTO], String)]

  def patchPartitioningParentV2(
     partitioningId: Long,
     parentPartitioningID: Long,
     byUser: String
 ): IO[ErrorResponse, SingleSuccessResponse[ParentPatchV2DTO]]

  def getPartitioningAdditionalData(
    partitioningId: Long
  ): IO[ErrorResponse, SingleSuccessResponse[AdditionalDataDTO]]

  def patchPartitioningAdditionalDataV2(
    partitioningId: Long,
    additionalDataPatchDTO: AdditionalDataPatchDTO
  ): IO[ErrorResponse, SingleSuccessResponse[AdditionalDataDTO]]

  def getPartitioningByIdV2(partitioningId: Long): IO[ErrorResponse, SingleSuccessResponse[PartitioningWithIdDTO]]

  def getPartitioningMeasuresV2(
    partitioningId: Long
  ): IO[ErrorResponse, MultiSuccessResponse[MeasureDTO]]

  def getPartitioning(
    partitioning: String
  ): IO[ErrorResponse, SingleSuccessResponse[PartitioningWithIdDTO]]

  def getFlowPartitionings(
    flowId: Long,
    limit: Option[Int],
    offset: Option[Long]
  ): IO[ErrorResponse, PaginatedResponse[PartitioningWithIdDTO]]

  def getPartitioningMainFlow(
    partitioningId: Long
  ): IO[ErrorResponse, SingleSuccessResponse[FlowDTO]]

  def getAncestors(
    partitioningId: Long,
    limit: Option[Int],
    offset: Option[Long]
  ): IO[ErrorResponse, PaginatedResponse[PartitioningWithIdDTO]]

}

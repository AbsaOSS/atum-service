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

import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.ErrorResponse
import za.co.absa.atum.model.envelopes.SuccessResponse._
import zio.IO
import zio.macros.accessible

@accessible
trait PartitioningController {

  def postPartitioning(
    partitioningSubmitDTO: PartitioningSubmitV2DTO
  ): IO[ErrorResponse, (SingleSuccessResponse[PartitioningWithIdDTO], String)]

  def getPartitioningAdditionalData(
    partitioningId: Long
  ): IO[ErrorResponse, SingleSuccessResponse[Map[String,Option[AdditionalDataItemDTO]]]]

  def patchPartitioningAdditionalData(
    partitioningId: Long,
    additionalDataPatchDTO: AdditionalDataPatchDTO
  ): IO[ErrorResponse, SingleSuccessResponse[Map[String,Option[AdditionalDataItemDTO]]]]

  def getPartitioningById(partitioningId: Long): IO[ErrorResponse, SingleSuccessResponse[PartitioningWithIdDTO]]

  def getPartitioningMeasures(
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

  def patchPartitioningParent(
    partitioningId: Long,
    partitioningParentPatchDTO: PartitioningParentPatchDTO
  ): IO[ErrorResponse, Unit]


  def getPartitioningAncestors(
    partitioningId: Long,
    limit: Option[Int],
    offset: Option[Long]
  ): IO[ErrorResponse, PaginatedResponse[PartitioningWithIdDTO]]

}

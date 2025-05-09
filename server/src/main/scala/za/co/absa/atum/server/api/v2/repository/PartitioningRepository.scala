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

package za.co.absa.atum.server.api.v2.repository

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.model.PaginatedResult
import zio.IO
import zio.macros.accessible

@accessible
trait PartitioningRepository {

  def createPartitioning(
    partitioningSubmitDTO: PartitioningSubmitV2DTO
  ): IO[DatabaseError, PartitioningWithIdDTO]

  def getPartitioningMeasures(partitioning: PartitioningDTO): IO[DatabaseError, Seq[MeasureDTO]]

  def getPartitioningAdditionalData(
    partitioningId: Long
  ): IO[DatabaseError, AdditionalDataDTO]

  def createOrUpdateAdditionalData(
    partitioningId: Long,
    additionalData: AdditionalDataPatchDTO
  ): IO[DatabaseError, AdditionalDataDTO]

  def getPartitioningById(partitioningId: Long): IO[DatabaseError, PartitioningWithIdDTO]

  def getPartitioningMeasuresById(partitioningId: Long): IO[DatabaseError, Seq[MeasureDTO]]

  def getPartitioning(
    partitioning: PartitioningDTO
  ): IO[DatabaseError, PartitioningWithIdDTO]

  def getFlowPartitionings(
    flowId: Long,
    limit: Option[Int],
    offset: Option[Long]
  ): IO[DatabaseError, PaginatedResult[PartitioningWithIdDTO]]

  def getPartitioningMainFlow(partitioningId: Long): IO[DatabaseError, FlowDTO]
}

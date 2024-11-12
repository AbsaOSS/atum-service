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

package za.co.absa.atum.server.api.service

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.repository.PartitioningRepository
import za.co.absa.atum.server.model.PaginatedResult
import zio._

class PartitioningServiceImpl(partitioningRepository: PartitioningRepository)
    extends PartitioningService
    with BaseService {

  override def createPartitioningIfNotExists(partitioningSubmitDTO: PartitioningSubmitDTO): IO[ServiceError, Unit] = {
    repositoryCall(
      partitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO),
      "createPartitioningIfNotExists"
    )
  }

  override def createPartitioning(
    partitioningSubmitDTO: PartitioningSubmitV2DTO
  ): IO[ServiceError, PartitioningWithIdDTO] = {
    repositoryCall(
      partitioningRepository.createPartitioning(partitioningSubmitDTO),
      "createPartitioning"
    )
  }

  override def getPartitioningMeasures(partitioning: PartitioningDTO): IO[ServiceError, Seq[MeasureDTO]] = {
    repositoryCall(
      partitioningRepository.getPartitioningMeasures(partitioning),
      "getPartitioningMeasures"
    )
  }

  override def getPartitioningAdditionalData(
    partitioning: PartitioningDTO
  ): IO[ServiceError, InitialAdditionalDataDTO] = {
    repositoryCall(
      partitioningRepository.getPartitioningAdditionalData(partitioning),
      "getPartitioningAdditionalData"
    )
  }

  override def getPartitioningAdditionalDataV2(partitioningId: Long): IO[ServiceError, AdditionalDataDTO] = {
    repositoryCall(
      partitioningRepository.getPartitioningAdditionalDataV2(partitioningId),
      "getPartitioningAdditionalDataV2"
    )
  }

  override def patchAdditionalData(
    partitioningId: Long,
    additionalData: AdditionalDataPatchDTO
  ): IO[ServiceError, AdditionalDataDTO] = {
    repositoryCall(
      partitioningRepository.createOrUpdateAdditionalData(partitioningId, additionalData),
      "createOrUpdateAdditionalData"
    )
  }

  override def getPartitioningById(partitioningId: Long): IO[ServiceError, PartitioningWithIdDTO] = {
    repositoryCall(partitioningRepository.getPartitioningById(partitioningId), "getPartitioning")
  }

  override def getPartitioningMeasuresById(partitioningId: Long): IO[ServiceError, Seq[MeasureDTO]] = {
    repositoryCall(
      partitioningRepository.getPartitioningMeasuresById(partitioningId),
      "getPartitioningMeasuresById"
    )
  }

  override def getFlowPartitionings(
    flowId: Long,
    limit: Option[Int],
    offset: Option[Long]
  ): IO[ServiceError, PaginatedResult[PartitioningWithIdDTO]] = {
    repositoryCall(
      partitioningRepository.getFlowPartitionings(flowId, limit, offset),
      "getFlowPartitionings"
    )
  }

  override def getPartitioning(
    partitioning: PartitioningDTO
  ): IO[ServiceError, PartitioningWithIdDTO] = {
    repositoryCall(
      partitioningRepository.getPartitioning(partitioning),
      "getPartitioning"
    )
  }

  override def getPartitioningMainFlow(partitioningId: Long): IO[ServiceError, FlowDTO] = {
    repositoryCall(
      partitioningRepository.getPartitioningMainFlow(partitioningId),
      "getPartitioningMainFlow"
    )
  }

  override def getAncestors(
     partitioningId: Long,
     limit: Option[Int],
     offset: Option[Long]
   ): IO[ServiceError, PaginatedResult[PartitioningWithIdDTO]] = {
    repositoryCall(
      partitioningRepository.getAncestors(partitioningId, limit, offset),
      "getAncestors"
    )
  }

}

object PartitioningServiceImpl {
  val layer: URLayer[PartitioningRepository, PartitioningService] = ZLayer {
    for {
      partitioningRepository <- ZIO.service[PartitioningRepository]
    } yield new PartitioningServiceImpl(partitioningRepository)
  }
}

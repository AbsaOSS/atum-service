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
import za.co.absa.atum.server.api.common.repository.BaseRepository
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings._
import za.co.absa.atum.server.api.database.flows.functions._
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningAncestors._
import za.co.absa.atum.server.api.database.runs.functions.CreateOrUpdateAdditionalData.CreateOrUpdateAdditionalDataArgs
import za.co.absa.atum.server.api.database.runs.functions.UpdatePartitioningParent.UpdatePartitioningParentArgs
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.atum.server.model._
import za.co.absa.atum.server.model.database.{
  AdditionalDataItemFromDB,
  MeasureFromDB,
  PartitioningForDB,
  PartitioningFromDB
}
import zio._
import zio.interop.catz.asyncInstance

class PartitioningRepositoryImpl(
  createPartitioningFn: CreatePartitioning,
  getPartitioningMeasuresFn: GetPartitioningMeasures,
  createOrUpdateAdditionalDataFn: CreateOrUpdateAdditionalData,
  getPartitioningAdditionalDataFn: GetPartitioningAdditionalData,
  getPartitioningByIdFn: GetPartitioningById,
  getPartitioningMeasuresByIdFn: GetPartitioningMeasuresById,
  getPartitioningFn: GetPartitioning,
  getFlowPartitioningsFn: GetFlowPartitionings,
  getPartitioningMainFlowFn: GetPartitioningMainFlow,
  updatePartitioningParentFn: UpdatePartitioningParent,
  getPartitioningAncestorsFn: GetPartitioningAncestors
) extends PartitioningRepository
    with BaseRepository {

  override def createPartitioning(
    partitioningSubmitDTO: PartitioningSubmitV2DTO
  ): IO[DatabaseError, PartitioningWithIdDTO] = {
    for {
      result <- dbSingleResultCallWithStatus(
        createPartitioningFn(partitioningSubmitDTO),
        "createPartitioning"
      )
    } yield PartitioningWithIdDTO(result, partitioningSubmitDTO.partitioning, partitioningSubmitDTO.author)
  }

  override def createOrUpdateAdditionalData(
    partitioningId: Long,
    additionalData: AdditionalDataPatchDTO
  ): IO[DatabaseError, Seq[AdditionalDataItemV2DTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      createOrUpdateAdditionalDataFn(CreateOrUpdateAdditionalDataArgs(partitioningId, additionalData)),
      "createOrUpdateAdditionalData"
    ).map(AdditionalDataItemFromDB.toSeqAdditionalDataItemV2DTO)
  }

  override def getPartitioningMeasures(partitioning: PartitioningDTO): IO[DatabaseError, Seq[MeasureDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(getPartitioningMeasuresFn(partitioning), "getPartitioningMeasures")
      .map(_.map { case MeasureFromDB(measureName, measuredColumns) =>
        MeasureDTO(measureName.get, measuredColumns.get)
      })
  }

  override def getPartitioningAdditionalData(partitioningId: Long): IO[DatabaseError, Seq[AdditionalDataItemV2DTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getPartitioningAdditionalDataFn(partitioningId),
      "getPartitioningAdditionalData"
    ).map(AdditionalDataItemFromDB.toSeqAdditionalDataItemV2DTO)
  }

  override def getPartitioningById(partitioningId: Long): IO[DatabaseError, PartitioningWithIdDTO] = {
    processPartitioningFromDBOptionIO(
      dbSingleResultCallWithStatus(getPartitioningByIdFn(partitioningId), "getPartitioningById")
    )
  }

  override def getPartitioningMeasuresById(partitioningId: Long): IO[DatabaseError, Seq[MeasureDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(getPartitioningMeasuresByIdFn(partitioningId), "getPartitioningMeasures")
      .map(_.map { case MeasureFromDB(measureName, measuredColumns) =>
        MeasureDTO(measureName.get, measuredColumns.get)
      })
  }

  override def getPartitioning(
    partitioning: PartitioningDTO
  ): IO[DatabaseError, PartitioningWithIdDTO] = {
    processPartitioningFromDBOptionIO(
      dbSingleResultCallWithStatus(
        getPartitioningFn(PartitioningForDB.fromSeqPartitionDTO(partitioning)),
        "getPartitioning"
      )
    )
  }

  private def processPartitioningFromDBOptionIO(
    partitioningFromDBOptionIO: IO[DatabaseError, Option[PartitioningFromDB]]
  ): IO[DatabaseError, PartitioningWithIdDTO] = {
    partitioningFromDBOptionIO.flatMap {
      case Some(PartitioningFromDB(id, partitioning, author)) =>
        val decodingResult = partitioning.as[PartitioningForDB]
        decodingResult.fold(
          error => ZIO.fail(GeneralDatabaseError(s"Failed to decode JSON: $error")),
          partitioningForDB => {
            val partitioningDTO: PartitioningDTO = partitioningForDB.keys.map { key =>
              PartitionDTO(key, partitioningForDB.keysToValuesMap(key))
            }
            ZIO.succeed(PartitioningWithIdDTO(id, partitioningDTO, author))
          }
        )
      case None => ZIO.fail(GeneralDatabaseError("Unexpected error."))
    }
  }
  override def getFlowPartitionings(
    flowId: Long,
    limit: Int,
    offset: Long
  ): IO[DatabaseError, PaginatedResult[PartitioningWithIdDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getFlowPartitioningsFn(GetFlowPartitioningsArgs(flowId, limit, offset)),
      "getFlowPartitionings"
    ).map(_.flatten)
      .flatMap { partitioningResults =>
        ZIO
          .fromEither(PartitioningResult.resultsToPartitioningWithIdDTOs(partitioningResults))
          .mapBoth(
            error => GeneralDatabaseError(error.getMessage),
            partitionings => {
              if (partitioningResults.nonEmpty && partitioningResults.head.hasMore) ResultHasMore(partitionings)
              else ResultNoMore(partitionings)
            }
          )
      }
  }

  override def getPartitioningMainFlow(partitioningId: Long): IO[DatabaseError, FlowDTO] = {
    dbSingleResultCallWithStatus(
      getPartitioningMainFlowFn(partitioningId),
      "getPartitioningMainFlow"
    ).flatMap {
      case Some(flowDTO) => ZIO.succeed(flowDTO)
      case None => ZIO.fail(GeneralDatabaseError("Unexpected error."))
    }
  }

  override def updatePartitioningParent(
    partitioningId: Long,
    partitioningParentPatchDTO: PartitioningParentPatchDTO
    ): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(
      updatePartitioningParentFn(UpdatePartitioningParentArgs(partitioningId, partitioningParentPatchDTO)),
      "updatePartitioningParent"
    )
  }

  override def getPartitioningAncestors(
     partitioningId: Long,
     limit: Int,
     offset: Long
   ): IO[DatabaseError, PaginatedResult[PartitioningWithIdDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getPartitioningAncestorsFn(GetPartitioningAncestorsArgs(partitioningId, limit, offset)),
      "getPartitioningAncestors"
    ).map(_.flatten)
      .flatMap { partitioningResults =>
        ZIO
          .fromEither(PartitioningResult.resultsToPartitioningWithIdDTOs(partitioningResults))
          .mapBoth(
            error => GeneralDatabaseError(error.getMessage),
            partitionings => {
              if (partitioningResults.nonEmpty && partitioningResults.head.hasMore) ResultHasMore(partitionings)
              else ResultNoMore(partitionings)
            }
          )
      }
  }

}

object PartitioningRepositoryImpl {
  val layer: URLayer[
    CreatePartitioning
      with GetPartitioningMeasures
      with CreateOrUpdateAdditionalData
      with GetPartitioningAdditionalData
      with GetPartitioningById
      with GetPartitioningMeasuresById
      with GetPartitioning
      with GetFlowPartitionings
      with GetPartitioningMainFlow
      with UpdatePartitioningParent
      with GetPartitioningAncestors,
    PartitioningRepository
  ] = ZLayer {
    for {
      createPartitioning <- ZIO.service[CreatePartitioning]
      getPartitioningMeasures <- ZIO.service[GetPartitioningMeasures]
      createOrUpdateAdditionalData <- ZIO.service[CreateOrUpdateAdditionalData]
      getPartitioningAdditionalData <- ZIO.service[GetPartitioningAdditionalData]
      getPartitioningById <- ZIO.service[GetPartitioningById]
      getPartitioningMeasuresById <- ZIO.service[GetPartitioningMeasuresById]
      getPartitioning <- ZIO.service[GetPartitioning]
      getFlowPartitionings <- ZIO.service[GetFlowPartitionings]
      getPartitioningMainFlow <- ZIO.service[GetPartitioningMainFlow]
      updatePartitioningParent <- ZIO.service[UpdatePartitioningParent]
      getPartitioningAncestors <- ZIO.service[GetPartitioningAncestors]
    } yield new PartitioningRepositoryImpl(
      createPartitioning,
      getPartitioningMeasures,
      createOrUpdateAdditionalData,
      getPartitioningAdditionalData,
      getPartitioningById,
      getPartitioningMeasuresById,
      getPartitioning,
      getFlowPartitionings,
      getPartitioningMainFlow,
      updatePartitioningParent,
      getPartitioningAncestors
    )
  }

}

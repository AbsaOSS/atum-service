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

package za.co.absa.atum.server.api.repository

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings._
import za.co.absa.atum.server.api.database.runs.functions.GetAncestors._
import za.co.absa.atum.server.api.database.runs.functions.CreateOrUpdateAdditionalData.CreateOrUpdateAdditionalDataArgs
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.database.flows.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.model._
import zio._
import zio.interop.catz.asyncInstance
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import PaginatedResult.{ResultHasMore, ResultNoMore}

class PartitioningRepositoryImpl(
  createPartitioningIfNotExistsFn: CreatePartitioningIfNotExists,
  createPartitioningFn: CreatePartitioning,
  getPartitioningMeasuresFn: GetPartitioningMeasures,
  createOrUpdateAdditionalDataFn: CreateOrUpdateAdditionalData,
  getPartitioningAdditionalDataFn: GetPartitioningAdditionalData,
  getPartitioningByIdFn: GetPartitioningById,
  getPartitioningMeasuresByIdFn: GetPartitioningMeasuresById,
  getPartitioningFn: GetPartitioning,
  getFlowPartitioningsFn: GetFlowPartitionings,
  getAncestorsFn: GetAncestors,
  //patchPartitioningParentFn: PatchPartitioningParent,
  getPartitioningMainFlowFn: GetPartitioningMainFlow

) extends PartitioningRepository
    with BaseRepository {

  override def createPartitioningIfNotExists(partitioningSubmitDTO: PartitioningSubmitDTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(
      createPartitioningIfNotExistsFn(partitioningSubmitDTO),
      "createPartitioningIfNotExists"
    )
  }

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
  ): IO[DatabaseError, AdditionalDataDTO] = {
    dbMultipleResultCallWithAggregatedStatus(
      createOrUpdateAdditionalDataFn(CreateOrUpdateAdditionalDataArgs(partitioningId, additionalData)),
      "createOrUpdateAdditionalData"
    ).map(AdditionalDataItemFromDB.additionalDataFromDBItems)
  }

  override def getPartitioningMeasures(partitioning: PartitioningDTO): IO[DatabaseError, Seq[MeasureDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(getPartitioningMeasuresFn(partitioning), "getPartitioningMeasures")
      .map(_.map { case MeasureFromDB(measureName, measuredColumns) =>
        MeasureDTO(measureName.get, measuredColumns.get)
      })
  }

  override def getPartitioningAdditionalData(partitioningId: Long): IO[DatabaseError, AdditionalDataDTO] = {
    dbMultipleResultCallWithAggregatedStatus(
      getPartitioningAdditionalDataFn(partitioningId),
      "getPartitioningAdditionalData"
    ).map(AdditionalDataItemFromDB.additionalDataFromDBItems)
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
    limit: Option[Int],
    offset: Option[Long]
  ): IO[DatabaseError, PaginatedResult[PartitioningWithIdDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getFlowPartitioningsFn(GetFlowPartitioningsArgs(flowId, limit, offset)),
      "getFlowPartitionings"
    ).map(_.flatten)
      .flatMap { partitioningResults =>
        ZIO
          .fromEither(GetFlowPartitioningsResult.resultsToPartitioningWithIdDTOs(partitioningResults, Seq.empty))
          .mapBoth(
            error => GeneralDatabaseError(error.getMessage),
            partitionings => {
              if (partitioningResults.nonEmpty && partitioningResults.head.hasMore) ResultHasMore(partitionings)
              else ResultNoMore(partitionings)
            }
          )
      }
  }

  override def getAncestors(
   partitioningId: Long,
   limit: Option[Int],
   offset: Option[Long]
  ): IO[DatabaseError, PaginatedResult[PartitioningWithIdDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getAncestorsFn(GetAncestorsArgs(partitioningId, limit, offset)),
      "getAncestors"
    ).map(_.flatten)
      .flatMap { partitioningResults =>
        ZIO
          .fromEither(GetAncestorsResult.resultsToPartitioningWithIdDTOs(partitioningResults, Seq.empty))
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

//  override def patchPartitioningParent(
//  partitioningId: Long,
//  parentPartitioningID: Long,
//  byUser: String
//  ): IO[DatabaseError, ParentPatchV2DTO] = {
//    dbSingleResultCallWithStatus(
//      patchPartitioningParentFn(PatchPartitioningParentArgs(partitioningId, parentPartitioningID, byUser)),
//      "patchPartitioningParent"
//    ).flatMap {
//      case Some(parentPatchV2DTO) => ZIO.succeed(parentPatchV2DTO)
//      case None => ZIO.fail(GeneralDatabaseError("Unexpected error."))
//    }
//  }

}

object PartitioningRepositoryImpl {
  val layer: URLayer[
    CreatePartitioningIfNotExists
      with CreatePartitioning
      with GetPartitioningMeasures
      with CreateOrUpdateAdditionalData
      with GetPartitioningAdditionalData
      with GetPartitioningById
      with GetPartitioningMeasuresById
      with GetPartitioning
      with GetFlowPartitionings
      with GetAncestors
      //with PatchPartitioningParent
      with GetPartitioningMainFlow,

    PartitioningRepository
  ] = ZLayer {
    for {
      createPartitioningIfNotExists <- ZIO.service[CreatePartitioningIfNotExists]
      createPartitioning <- ZIO.service[CreatePartitioning]
      getPartitioningMeasures <- ZIO.service[GetPartitioningMeasures]
      createOrUpdateAdditionalData <- ZIO.service[CreateOrUpdateAdditionalData]
      getPartitioningAdditionalData <- ZIO.service[GetPartitioningAdditionalData]
      getPartitioningById <- ZIO.service[GetPartitioningById]
      getPartitioningMeasuresById <- ZIO.service[GetPartitioningMeasuresById]
      getPartitioning <- ZIO.service[GetPartitioning]
      getFlowPartitionings <- ZIO.service[GetFlowPartitionings]
      getPartitioningMainFlow <- ZIO.service[GetPartitioningMainFlow]
      getAncestors <- ZIO.service[GetAncestors]
      //patchPartitioningParent <- ZIO.service[PatchPartitioningParent]
    } yield new PartitioningRepositoryImpl(
      createPartitioningIfNotExists,
      createPartitioning,
      getPartitioningMeasures,
      createOrUpdateAdditionalData,
      getPartitioningAdditionalData,
      getPartitioningById,
      getPartitioningMeasuresById,
      getPartitioning,
      getFlowPartitionings,
      getAncestors,
      //patchPartitioningParent,
      getPartitioningMainFlow

    )
  }

}

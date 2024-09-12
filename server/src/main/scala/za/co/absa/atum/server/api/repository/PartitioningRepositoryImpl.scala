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
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings.GetFlowPartitioningsArgs
import za.co.absa.atum.server.api.database.runs.functions.CreateOrUpdateAdditionalData.CreateOrUpdateAdditionalDataArgs
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.model.{AdditionalDataFromDB, AdditionalDataItemFromDB, CheckpointFromDB, MeasureFromDB, PaginatedResult, PartitioningFromDB}
import zio._
import zio.interop.catz.asyncInstance
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}

class PartitioningRepositoryImpl(
  createPartitioningIfNotExistsFn: CreatePartitioningIfNotExists,
  getPartitioningMeasuresFn: GetPartitioningMeasures,
  getPartitioningAdditionalDataFn: GetPartitioningAdditionalData,
  createOrUpdateAdditionalDataFn: CreateOrUpdateAdditionalData,
  getPartitioningCheckpointsFn: GetPartitioningCheckpoints,
  getPartitioningByIdFn: GetPartitioningById,
  getPartitioningAdditionalDataV2Fn: GetPartitioningAdditionalDataV2,
  getPartitioningMeasuresByIdFn: GetPartitioningMeasuresById,
  getFlowPartitioningsFn: GetFlowPartitionings
) extends PartitioningRepository
    with BaseRepository {

  override def createPartitioningIfNotExists(partitioningSubmitDTO: PartitioningSubmitDTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(
      createPartitioningIfNotExistsFn(partitioningSubmitDTO),
      "createPartitioningIfNotExists"
    )
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

  override def getPartitioningAdditionalData(
    partitioning: PartitioningDTO
  ): IO[DatabaseError, InitialAdditionalDataDTO] = {
    dbMultipleResultCallWithAggregatedStatus(
      getPartitioningAdditionalDataFn(partitioning),
      "getPartitioningAdditionalData"
    ).map(_.map { case AdditionalDataFromDB(adName, adValue) => adName.get -> adValue }.toMap)
  }

  override def getPartitioningCheckpoints(
    checkpointQueryDTO: CheckpointQueryDTO
  ): IO[DatabaseError, Seq[CheckpointFromDB]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getPartitioningCheckpointsFn(checkpointQueryDTO),
      "getPartitioningCheckpoints"
    )
  }

  override def getPartitioningAdditionalDataV2(partitioningId: Long): IO[DatabaseError, AdditionalDataDTO] = {
    dbMultipleResultCallWithAggregatedStatus(
      getPartitioningAdditionalDataV2Fn(partitioningId),
      "getPartitioningAdditionalData"
    ).map(AdditionalDataItemFromDB.additionalDataFromDBItems)
  }

  override def getPartitioning(partitioningId: Long): IO[DatabaseError, PartitioningWithIdDTO] = {
    dbSingleResultCallWithStatus(getPartitioningByIdFn(partitioningId), "getPartitioningById")
      .flatMap {
        case Some(PartitioningFromDB(id, partitioning, author)) =>
          val decodingResult = partitioning.as[PartitioningDTO]
          decodingResult.fold(
            error => ZIO.fail(GeneralDatabaseError(s"Failed to decode JSON: $error")),
            partitioningDTO => ZIO.succeed(PartitioningWithIdDTO(id, partitioningDTO, author))
          )
        case None => ZIO.fail(GeneralDatabaseError("Unexpected error."))
      }
  }

  override def getPartitioningMeasuresById(partitioningId: Long): IO[DatabaseError, Seq[MeasureDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(getPartitioningMeasuresByIdFn(partitioningId), "getPartitioningMeasures")
      .map(_.map { case MeasureFromDB(measureName, measuredColumns) =>
        MeasureDTO(measureName.get, measuredColumns.get)
      })
  }

  override def getFlowPartitionings(
    flowId: Long,
    limit: Option[RuntimeFlags],
    offset: Option[Long]
  ): IO[DatabaseError, PaginatedResult[PartitioningWithIdDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(
      getFlowPartitioningsFn(GetFlowPartitioningsArgs(flowId, limit, offset)),
      "getFlowPartitionings"
    )
      .map(x => _.flatten)
      .flatMap { checkpointItems =>
//        ZIO
//          .fromEither(CheckpointItemFromDB.groupAndConvertItemsToCheckpointV2DTOs(checkpointItems))
//          .mapBoth(
//            error => GeneralDatabaseError(error.getMessage),
//            checkpoints =>
//              if (checkpointItems.nonEmpty && checkpointItems.head.hasMore) ResultHasMore(checkpoints)
//              else ResultNoMore(checkpoints)
//          )
//      }
  }
}

object PartitioningRepositoryImpl {
  val layer: URLayer[
    CreatePartitioningIfNotExists
      with GetPartitioningMeasures
      with GetPartitioningAdditionalData
      with CreateOrUpdateAdditionalData
      with GetPartitioningCheckpoints
      with GetPartitioningAdditionalDataV2
      with GetPartitioningById
      with GetPartitioningMeasuresById
      with GetFlowPartitionings,
    PartitioningRepository
  ] = ZLayer {
    for {
      createPartitioningIfNotExists <- ZIO.service[CreatePartitioningIfNotExists]
      getPartitioningMeasures <- ZIO.service[GetPartitioningMeasures]
      getPartitioningAdditionalData <- ZIO.service[GetPartitioningAdditionalData]
      createOrUpdateAdditionalData <- ZIO.service[CreateOrUpdateAdditionalData]
      getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
      getPartitioningById <- ZIO.service[GetPartitioningById]
      getPartitioningAdditionalDataV2 <- ZIO.service[GetPartitioningAdditionalDataV2]
      getPartitioningMeasuresV2 <- ZIO.service[GetPartitioningMeasuresById]
      getFlowPartitionings <- ZIO.service[GetFlowPartitionings]
    } yield new PartitioningRepositoryImpl(
      createPartitioningIfNotExists,
      getPartitioningMeasures,
      getPartitioningAdditionalData,
      createOrUpdateAdditionalData,
      getPartitioningCheckpoints,
      getPartitioningById,
      getPartitioningAdditionalDataV2,
      getPartitioningMeasuresV2,
      getFlowPartitionings
    )
  }
}

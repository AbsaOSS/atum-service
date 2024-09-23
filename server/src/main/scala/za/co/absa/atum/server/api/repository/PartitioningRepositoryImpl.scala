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
import za.co.absa.atum.server.api.database.runs.functions.CreateOrUpdateAdditionalData.CreateOrUpdateAdditionalDataArgs
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.model._
import zio._
import zio.interop.catz.asyncInstance
import za.co.absa.atum.server.api.exception.DatabaseError.GeneralDatabaseError

import scala.collection.immutable.ListMap

class PartitioningRepositoryImpl(
  createPartitioningIfNotExistsFn: CreatePartitioningIfNotExists,
  createPartitioningFn: CreatePartitioning,
  getPartitioningMeasuresFn: GetPartitioningMeasures,
  getPartitioningAdditionalDataFn: GetPartitioningAdditionalData,
  createOrUpdateAdditionalDataFn: CreateOrUpdateAdditionalData,
  getPartitioningCheckpointsFn: GetPartitioningCheckpoints,
  getPartitioningByIdFn: GetPartitioningById,
  getPartitioningAdditionalDataV2Fn: GetPartitioningAdditionalDataV2,
  getPartitioningMeasuresByIdFn: GetPartitioningMeasuresById
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

  override def getPartitioningById(partitioningId: Long): IO[DatabaseError, PartitioningWithIdDTO] = {
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

  override def getPartitioning(
    partitioning: PartitioningDTO
  ): IO[DatabaseError, PartitioningWithIdDTO] = {
    ???
  }
}

object PartitioningRepositoryImpl {
  val layer: URLayer[
    CreatePartitioningIfNotExists
      with CreatePartitioning
      with GetPartitioningMeasures
      with GetPartitioningAdditionalData
      with CreateOrUpdateAdditionalData
      with GetPartitioningCheckpoints
      with GetPartitioningAdditionalDataV2
      with GetPartitioningById
      with GetPartitioningMeasuresById,
    PartitioningRepository
  ] = ZLayer {
    for {
      createPartitioningIfNotExists <- ZIO.service[CreatePartitioningIfNotExists]
      createPartitioning <- ZIO.service[CreatePartitioning]
      getPartitioningMeasures <- ZIO.service[GetPartitioningMeasures]
      getPartitioningAdditionalData <- ZIO.service[GetPartitioningAdditionalData]
      createOrUpdateAdditionalData <- ZIO.service[CreateOrUpdateAdditionalData]
      getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
      getPartitioningById <- ZIO.service[GetPartitioningById]
      getPartitioningAdditionalDataV2 <- ZIO.service[GetPartitioningAdditionalDataV2]
      getPartitioningMeasuresV2 <- ZIO.service[GetPartitioningMeasuresById]
    } yield new PartitioningRepositoryImpl(
      createPartitioningIfNotExists,
      createPartitioning,
      getPartitioningMeasures,
      getPartitioningAdditionalData,
      createOrUpdateAdditionalData,
      getPartitioningCheckpoints,
      getPartitioningById,
      getPartitioningAdditionalDataV2,
      getPartitioningMeasuresV2
    )
  }
}

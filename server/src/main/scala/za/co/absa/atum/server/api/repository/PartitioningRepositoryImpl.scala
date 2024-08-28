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
import za.co.absa.atum.server.model.{AdditionalDataFromDB, AdditionalDataItemFromDB, CheckpointFromDB, MeasureFromDB}
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import zio._
import zio.interop.catz.asyncInstance

class PartitioningRepositoryImpl(
  createPartitioningIfNotExistsFn: CreatePartitioningIfNotExists,
  getPartitioningMeasuresFn: GetPartitioningMeasures,
  getPartitioningAdditionalDataFn: GetPartitioningAdditionalData,
  createOrUpdateAdditionalDataFn: CreateOrUpdateAdditionalData,
  getPartitioningCheckpointsFn: GetPartitioningCheckpoints,
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

  override def createOrUpdateAdditionalData(additionalData: AdditionalDataSubmitDTO): IO[DatabaseError, Unit] = {
    dbSingleResultCallWithStatus(createOrUpdateAdditionalDataFn(additionalData), "createOrUpdateAdditionalData")
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
    ).map(_.collect { case Some(AdditionalDataItemFromDB(adName, adValue, author)) =>
      adName -> Some(AdditionalDataItemDTO(adValue, author))
    }.toMap)
      .map(AdditionalDataDTO(_))
  }

  override def getPartitioningMeasuresById(partitioningId: Long): IO[DatabaseError, Seq[MeasureDTO]] = {
    dbMultipleResultCallWithAggregatedStatus(getPartitioningMeasuresByIdFn(partitioningId), "getPartitioningMeasures")
      .map(_.map { case MeasureFromDB(measureName, measuredColumns) =>
        MeasureDTO(measureName.get, measuredColumns.get)
      })
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
      with GetPartitioningCheckpoints
      with GetPartitioningMeasuresById,
    PartitioningRepository
  ] = ZLayer {
    for {
      createPartitioningIfNotExists <- ZIO.service[CreatePartitioningIfNotExists]
      getPartitioningMeasures <- ZIO.service[GetPartitioningMeasures]
      getPartitioningAdditionalData <- ZIO.service[GetPartitioningAdditionalData]
      createOrUpdateAdditionalData <- ZIO.service[CreateOrUpdateAdditionalData]
      getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
      getPartitioningAdditionalDataV2 <- ZIO.service[GetPartitioningAdditionalDataV2]
      getPartitioningMeasuresV2 <- ZIO.service[GetPartitioningMeasuresById]
    } yield new PartitioningRepositoryImpl(
      createPartitioningIfNotExists,
      getPartitioningMeasures,
      getPartitioningAdditionalData,
      createOrUpdateAdditionalData,
      getPartitioningCheckpoints,
      getPartitioningAdditionalDataV2,
      getPartitioningMeasuresV2
    )
  }
}

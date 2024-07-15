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

import shapeless.syntax.std.product.productOps
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataSubmitDTO, CheckpointQueryDTO, MeasureDTO, PartitioningDTO, PartitioningSubmitDTO}
import za.co.absa.atum.server.api.database.runs.functions.{CreateOrUpdateAdditionalData, CreatePartitioningIfNotExists, GetPartitioningAdditionalData, GetPartitioningCheckpoints, GetPartitioningMeasures}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.model.CheckpointFromDB
import za.co.absa.db.fadb.exceptions.StatusException
import za.co.absa.db.fadb.status
import zio._
import zio.interop.catz.asyncInstance
import zio.prelude.ZivariantOps
import zio.prelude.data.Optional.AllValuesAreNullable

class PartitioningRepositoryImpl(
  createPartitioningIfNotExistsFn: CreatePartitioningIfNotExists,
  getPartitioningMeasuresFn: GetPartitioningMeasures,
  getPartitioningAdditionalDataFn: GetPartitioningAdditionalData,
  createOrUpdateAdditionalDataFn: CreateOrUpdateAdditionalData,
  getPartitioningCheckpointsFn: GetPartitioningCheckpoints
) extends PartitioningRepository with BaseRepository {

  override def createPartitioningIfNotExists(
    partitioningSubmitDTO: PartitioningSubmitDTO
  ): IO[DatabaseError, Either[StatusException, status.Row[Unit]]] = {
    dbCallWithStatus(createPartitioningIfNotExistsFn(partitioningSubmitDTO), "createPartitioningIfNotExists")
  }

  override def createOrUpdateAdditionalData(
    additionalData: AdditionalDataSubmitDTO
  ): IO[DatabaseError, Either[StatusException, status.Row[Unit]]] = {
    dbCallWithStatus(createOrUpdateAdditionalDataFn(additionalData), "createOrUpdateAdditionalData")
  }

  override def getPartitioningMeasures(
    partitioning: PartitioningDTO
  ): IO[DatabaseError, Either[StatusException, Seq[status.Row[MeasureDTO]]]] = {
    dbCallWithStatus(getPartitioningMeasuresFn(partitioning), "getPartitioningMeasures")
  }

  override def getPartitioningAdditionalData(partitioning: PartitioningDTO):
  IO[DatabaseError, Either[StatusException, status.Row[AdditionalDataDTO]]] = {
    dbCallWithStatus(getPartitioningAdditionalDataFn(partitioning)
      .map(_.toMap.getOrElse("additional_data", null)),
      "getPartitioningAdditionalData")
  }
  // mapBoth(err => DatabaseError(err.getMessage), _.toMap)

  override def getPartitioningCheckpoints(checkpointQueryDTO: CheckpointQueryDTO):
  IO[DatabaseError, Either[StatusException, Seq[status.Row[CheckpointFromDB]]]] = {
    dbCallWithStatus(getPartitioningCheckpointsFn(checkpointQueryDTO), "getPartitioningCheckpoints")
  }
}

object PartitioningRepositoryImpl {
  val layer: URLayer[
    CreatePartitioningIfNotExists
      with GetPartitioningMeasures
      with GetPartitioningAdditionalData
      with CreateOrUpdateAdditionalData
      with GetPartitioningCheckpoints,
    PartitioningRepository
  ] = ZLayer {
    for {
      createPartitioningIfNotExists <- ZIO.service[CreatePartitioningIfNotExists]
      getPartitioningMeasures <- ZIO.service[GetPartitioningMeasures]
      getPartitioningAdditionalData <- ZIO.service[GetPartitioningAdditionalData]
      createOrUpdateAdditionalData <- ZIO.service[CreateOrUpdateAdditionalData]
      getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
    } yield new PartitioningRepositoryImpl(
      createPartitioningIfNotExists,
      getPartitioningMeasures,
      getPartitioningAdditionalData,
      createOrUpdateAdditionalData,
      getPartitioningCheckpoints)
  }
}

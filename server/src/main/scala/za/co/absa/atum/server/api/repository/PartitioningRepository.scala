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

import za.co.absa.atum.model.dto.{MeasureDTO, PartitioningSubmitDTO}
import za.co.absa.atum.server.api.database.runs.functions.{CreatePartitioningIfNotExists, GetPartitioningMeasures}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.fadb.exceptions.StatusException
import zio._
import zio.macros.accessible

@accessible
trait PartitioningRepository {
  def createPartitioningIfNotExists(
    partitioning: PartitioningSubmitDTO
  ): IO[DatabaseError, Either[StatusException, Unit]]

  def getPartitioningMeasures(
    partitioning: PartitioningSubmitDTO
  ): IO[DatabaseError, Either[StatusException, Seq[MeasureDTO]]]
}

class PartitioningRepositoryImpl(createPartitioningIfNotExistsFn: CreatePartitioningIfNotExists,
                                 getPartitioningMeasuresFn: GetPartitioningMeasures)
    extends PartitioningRepository {
  override def createPartitioningIfNotExists(
    partitioning: PartitioningSubmitDTO
  ): IO[DatabaseError, Either[StatusException, Unit]] = {
    createPartitioningIfNotExistsFn(partitioning)
      .tap {
        case Left(statusException) =>
          ZIO.logError(
            s"Partitioning create or retrieve operation exception: (${statusException.status.statusCode}) ${statusException.status.statusText}"
          )
        case Right(_) =>
          ZIO.logDebug("Partitioning successfully created or retrieved in/from database.")
      }
      .mapError(error => DatabaseError(error.getMessage))
      .tapError(error => ZIO.logError(s"Failed to create or retrieve partitioning in/from database: ${error.message}"))
  }

  override def getPartitioningMeasures(partitioning: PartitioningSubmitDTO): IO[DatabaseError, Either[StatusException, Seq[MeasureDTO]]] = {
    getPartitioningMeasuresFn(partitioning)
      .tap {
        case Left(statusException) =>
          ZIO.logError(
            s"Partitioning measures retrieve operation exception: (${statusException.status.statusCode}) ${statusException.status.statusText}"
          )
        case Right(_) =>
          ZIO.logDebug("Partitioning measures successfully retrieved from database.")
      }
      .mapError(error => DatabaseError(error.getMessage))
      .tapError(error => ZIO.logError(s"Failed to retrieve partitioning measures from database: ${error.message}"))
  }
}

object PartitioningRepositoryImpl {
  val layer: URLayer[CreatePartitioningIfNotExists, PartitioningRepository] = ZLayer {
    for {
      createPartitioningIfNotExists <- ZIO.service[CreatePartitioningIfNotExists]
    } yield new PartitioningRepositoryImpl(createPartitioningIfNotExists)
  }
}

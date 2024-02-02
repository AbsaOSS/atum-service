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

import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, PartitioningSubmitDTO}
import za.co.absa.atum.server.api.database.runs.functions.{CreateOrUpdateAdditionalData, CreatePartitioningIfNotExists}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.fadb.exceptions.StatusException
import zio._
import zio.macros.accessible

@accessible
trait PartitioningRepository {
  def createPartitioningIfNotExists(
    partitioning: PartitioningSubmitDTO
  ): IO[DatabaseError, Either[StatusException, Unit]]


  def createOrUpdateAdditionalData(
    additionalData: AdditionalDataSubmitDTO
  ): IO[DatabaseError, Either[StatusException, Unit]]
}

class PartitioningRepositoryImpl(
    createPartitioningIfNotExistsFn: CreatePartitioningIfNotExists,
    createOrUpdateAdditionalData: CreateOrUpdateAdditionalData
  ) extends PartitioningRepository {
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

  override def createOrUpdateAdditionalData(
    additionalData: AdditionalDataSubmitDTO
  ): IO[DatabaseError, Either[StatusException, Unit]] = {
    createOrUpdateAdditionalData(additionalData)
      .tap {
        case Left(statusException) =>
          ZIO.logError(
            s"Additional data create or update operation exception: " +
              s"(${statusException.status.statusCode}) ${statusException.status.statusText}"
          )
        case Right(_) =>
          ZIO.logDebug("Additional data successfully created or updated in database.")
      }
      .mapError(error => DatabaseError(error.getMessage))
      .tapError(error => ZIO.logError(s"Failed to create or update additional data in database: ${error.message}"))
  }
}

object PartitioningRepositoryImpl {
  // TODO!
  val layer: URLayer[(CreatePartitioningIfNotExists, CreateOrUpdateAdditionalData), PartitioningRepository] = ZLayer {
    for {
      createPartitioningIfNotExists <- ZIO.service[CreatePartitioningIfNotExists]
      createOrUpdateAdditionalData <- ZIO.service[CreateOrUpdateAdditionalData]
    } yield new PartitioningRepositoryImpl(createPartitioningIfNotExists, createOrUpdateAdditionalData)
  }
}

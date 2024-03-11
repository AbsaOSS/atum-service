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

import za.co.absa.atum.model.dto.PartitioningSubmitDTO
import za.co.absa.atum.server.api.exception.{DatabaseError, ServiceError}
import za.co.absa.atum.server.api.repository.PartitioningRepository
import za.co.absa.fadb.exceptions.StatusException
import zio._
import zio.macros.accessible

@accessible
trait PartitioningService {
  def createPartitioningIfNotExists(
    partitioning: PartitioningSubmitDTO
  ): IO[ServiceError, Either[StatusException, Unit]]

  def GetPartitioningMeasures(
    partitioning: PartitioningSubmitDTO
  ): IO[ServiceError, Either[StatusException, Unit]]
}

class PartitioningServiceImpl(partitioningRepository: PartitioningRepository) extends PartitioningService {
  override def createPartitioningIfNotExists(
    partitioning: PartitioningSubmitDTO
  ): IO[ServiceError, Either[StatusException, Unit]] = {
    partitioningRepository
      .createPartitioningIfNotExists(partitioning)
      .mapError { case DatabaseError(message) =>
        ServiceError(s"Failed to create or retrieve partitioning: $message")
      }
  }

  override def GetPartitioningMeasures(partitioning: PartitioningSubmitDTO): IO[ServiceError, Either[StatusException, Unit]] = {
    partitioningRepository
      .getPartitioningMeasures(partitioning)
      .mapError { case DatabaseError(message) =>
        ServiceError(s"Failed to retrieve partitioning measures: $message")
      }
  }
}

object PartitioningServiceImpl {
  val layer: URLayer[PartitioningRepository, PartitioningService] = ZLayer {
    for {
      partitioningRepository <- ZIO.service[PartitioningRepository]
    } yield new PartitioningServiceImpl(partitioningRepository)
  }
}

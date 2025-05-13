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

package za.co.absa.atum.server.api.v1.service

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.api.common.service.BaseService
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.v1.repository.PartitioningRepository
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

}

object PartitioningServiceImpl {
  val layer: URLayer[PartitioningRepository, PartitioningService] = ZLayer {
    for {
      partitioningRepository <- ZIO.service[PartitioningRepository]
    } yield new PartitioningServiceImpl(partitioningRepository)
  }
}

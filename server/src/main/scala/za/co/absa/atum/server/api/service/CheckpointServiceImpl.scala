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

import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.repository.CheckpointRepository
import za.co.absa.fadb.exceptions.StatusException
import zio._

class CheckpointServiceImpl(checkpointRepository: CheckpointRepository)
  extends CheckpointService with BaseService {

  override def saveCheckpoint(checkpointDTO: CheckpointDTO): IO[ServiceError, Either[StatusException, Unit]] = {
    repositoryCallWithStatus(
      checkpointRepository.writeCheckpoint(checkpointDTO), "saveCheckpoint"
    )
  }

}

object CheckpointServiceImpl {
  val layer: URLayer[CheckpointRepository, CheckpointService] = ZLayer {
    for {
      checkpointRepository <- ZIO.service[CheckpointRepository]
    } yield new CheckpointServiceImpl(checkpointRepository)
  }
}

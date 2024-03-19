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

package za.co.absa.atum.server.api.controller

import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.api.service.CheckpointService
import za.co.absa.atum.server.model.ErrorResponse
import zio._

class CheckpointControllerImpl(checkpointService: CheckpointService)
  extends CheckpointController with BaseController {

  override def createCheckpoint(checkpointDTO: CheckpointDTO): IO[ErrorResponse, CheckpointDTO] = {
    serviceCallWithStatus[Unit, CheckpointDTO](
      checkpointService.saveCheckpoint(checkpointDTO),
      _ => checkpointDTO
    )
  }

}

object CheckpointControllerImpl {
  val layer: URLayer[CheckpointService, CheckpointController] = ZLayer {
    for {
      checkpointService <- ZIO.service[CheckpointService]
    } yield new CheckpointControllerImpl(checkpointService)
  }
}

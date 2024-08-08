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

import za.co.absa.atum.model.dto.{CheckpointDTO, CheckpointV2DTO}
import za.co.absa.atum.server.model.ErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio.IO
import zio.macros.accessible

@accessible
trait CheckpointController {

  def createCheckpointV1(checkpointDTO: CheckpointDTO): IO[ErrorResponse, CheckpointDTO]

  def postCheckpointV2(
    partitioningId: Long,
    checkpointDTO: CheckpointDTO
  ): IO[ErrorResponse, (SingleSuccessResponse[CheckpointDTO], String)]

  def getPartitioningCheckpointV2(
    partitioningId: Long,
    checkpointId: String
  ): IO[ErrorResponse, SingleSuccessResponse[CheckpointV2DTO]]

}

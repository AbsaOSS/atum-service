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

import za.co.absa.atum.model.dto.{
  AdditionalDataSubmitDTO,
  AtumContextDTO,
  CheckpointDTO,
  CheckpointQueryDTO,
  PartitioningSubmitDTO
}
import za.co.absa.atum.server.model.ErrorResponse
import za.co.absa.atum.server.model.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import zio.IO
import zio.macros.accessible

@accessible
trait PartitioningController {
  def createPartitioningIfNotExistsV1(
    partitioningSubmitDTO: PartitioningSubmitDTO
  ): IO[ErrorResponse, AtumContextDTO]

  def createPartitioningIfNotExistsV2(
    partitioningSubmitDTO: PartitioningSubmitDTO
  ): IO[ErrorResponse, SingleSuccessResponse[AtumContextDTO]]

  def createOrUpdateAdditionalDataV2(
    additionalData: AdditionalDataSubmitDTO
  ): IO[ErrorResponse, SingleSuccessResponse[AdditionalDataSubmitDTO]]

  def getPartitioningCheckpointsV2(
    checkpointQueryDTO: CheckpointQueryDTO
  ): IO[ErrorResponse, MultiSuccessResponse[CheckpointDTO]]
}

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

package za.co.absa.atum.agent.dispatcher

import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, AtumContextDTO, CheckpointDTO, PartitioningSubmitDTO}

/**
 *  This trait provides a contract for different dispatchers
 */
trait Dispatcher {
  /**
   *  This method is used to ensure the server knows the given partitioning.
   *  As a response the `AtumContext` is fetched from the server.
   *  @param partitioning: PartitioningSubmitDTO to be used to ensure server knows the given partitioning.
   *  @return AtumContextDTO.
   */
  def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO

  /**
   *  This method is used to save checkpoint to server.
   *  @param checkpoint: CheckpointDTO to be saved.
   */
  def saveCheckpoint(checkpoint: CheckpointDTO): Unit

  /**
   * This method is used to save the metadata to the server.
   * @param AdditionalData the data to be saved.
   */
  def saveAdditionalData(AdditionalData: AdditionalDataSubmitDTO): Unit
}

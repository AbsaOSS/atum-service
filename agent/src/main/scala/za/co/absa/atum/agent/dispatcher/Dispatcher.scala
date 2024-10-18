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

import com.typesafe.config.Config
import za.co.absa.atum.model.dto.{
  AdditionalDataDTO,
  AdditionalDataPatchDTO,
  AtumContextDTO,
  CheckpointDTO,
  PartitioningDTO,
  PartitioningSubmitDTO
}

/**
 *  This class provides a contract for different dispatchers. It has a constructor foe eventual creation via reflection.
 *  @param config: Config to be used to create the dispatcher.
 */
abstract class Dispatcher(config: Config) {


  /**
   *  This method is used to ensure the server knows the given partitioning.
   *  As a response the `AtumContext` is fetched from the server.
   *  @param partitioning: PartitioningSubmitDTO to be used to ensure server knows the given partitioning.
   *  @return AtumContextDTO.
   */
  protected[agent] def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO

  /**
   *  This method is used to save checkpoint to server.
   *  @param checkpoint: CheckpointDTO to be saved.
   */
  protected[agent] def saveCheckpoint(checkpoint: CheckpointDTO): Unit

  /**
   *  This method is used to save the additional data to the server.
   *  @param partitioning partitioning for which the additional data is to be saved.
   *  @param additionalDataPatchDTO the data to be saved or updated if already existing.
   */
  protected[agent] def updateAdditionalData(
    partitioning: PartitioningDTO,
    additionalDataPatchDTO: AdditionalDataPatchDTO
  ): AdditionalDataDTO

}

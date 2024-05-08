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
import org.apache.spark.internal.Logging
import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, AtumContextDTO, CheckpointSubmitDTO, PartitioningSubmitDTO}

/**
 *  dispatcher useful for development, testing and debugging
 */
class ConsoleDispatcher(config: Config) extends Dispatcher(config: Config) with Logging {

  logInfo("using console dispatcher")

  override protected[agent] def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO = {
    println(s"Fetching AtumContext using ConsoleDispatcher with partitioning $partitioning")
    AtumContextDTO(partitioning = partitioning.partitioning)
  }

  override protected[agent] def saveCheckpoint(checkpoint: CheckpointSubmitDTO): Unit = {
    println(s"Saving checkpoint to server. $checkpoint")
  }

  override protected[agent] def saveAdditionalData(additionalData: AdditionalDataSubmitDTO): Unit = {
    println(s"Saving the additional data to server. $additionalData")
  }

}

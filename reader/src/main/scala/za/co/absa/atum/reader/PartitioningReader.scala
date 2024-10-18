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

package za.co.absa.atum.reader

import com.typesafe.config.Config
import io.circe.syntax.EncoderOps
//import za.co.absa.atum.agent.AtumAgent.dispatcher
import za.co.absa.atum.agent.dispatcher.HttpDispatcher
import za.co.absa.atum.model.dto.{CheckpointV2DTO, PartitioningDTO}

class PartitioningReader(config: Config) {

  private val dispatcher: HttpDispatcher = new HttpDispatcher(config)

  /**
   * Fetches additional data for the given partitioning.
   * @param partitioning The partitioning for which to fetch additional data.
   * @return AdditionalDataDTO containing the additional data.
   */
  def getAdditionalData(partitioning: PartitioningDTO): String = {
    dispatcher.getAdditionalData(partitioning).asJson.noSpaces
  }

  /**
   * Fetches checkpoints for the given partitioning.
   * @param partitioning The partitioning for which to fetch checkpoints.
   * @return List of CheckpointDTO containing the checkpoints.
   */
  def getCheckpoints(partitioning: PartitioningDTO): List[CheckpointV2DTO] = {
    dispatcher.getCheckpoints(partitioning).applyOrElse(partitioning, _ => List.empty)
  }

}

object PartitioningReader {
  def apply(config: Config): PartitioningReader = new PartitioningReader(config)

  def apply(): PartitioningReader = new PartitioningReader(null)

  def apply(config: Config, partitioning: PartitioningDTO): PartitioningReader = {
    val reader = new PartitioningReader(config)
    reader.getAdditionalData(partitioning)
    reader.getCheckpoints(partitioning)
    reader
  }
}

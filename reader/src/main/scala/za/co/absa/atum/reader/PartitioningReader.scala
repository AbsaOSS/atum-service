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

import cats.Monad
import za.co.absa.atum.model.types.BasicTypes.{AdditionalData, AtumPartitions}
import za.co.absa.atum.model.types.Checkpoint
import za.co.absa.atum.reader.server.GenericServerConnection
import za.co.absa.atum.agent.AtumAgent.dispatcher
import za.co.absa.atum.agent.dispatcher.HttpDispatcher

class PartitioningReader[F[_] : Monad](partitioning: AtumPartitions)(implicit serverConnection : GenericServerConnection[F]) {

  /**
   * Fetches additional data for the given partitioning.
   * @param partitioning The partitioning for which to fetch additional data.
   * @return AdditionalData containing the additional data.
   */
  def getAdditionalData(): F[AdditionalData] = {
    // dispatcher.getAdditionalData(partitioning).asJson.noSpaces
    // call uri to get id
    // call the get partitioningAdditionalData
    // transform envelope additionalDataDTO into AdditionalData
//    serverConnection.getAdditionalData(partitioning)
  }

  /**
   * Fetches checkpoints for the given partitioning.
   * @param partitioning The partitioning for which to fetch checkpoints.
   * @return List of CheckpointDTO containing the checkpoints.
   */
  def getCheckpoints(): List[Checkpoint] = {
    // Add optional parameters here.
//    dispatcher.getCheckpoints(partitioning).applyOrElse(partitioning, _ => List.empty)
  }

}

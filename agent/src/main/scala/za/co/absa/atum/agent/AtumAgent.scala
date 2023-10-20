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

package za.co.absa.atum.agent
import com.typesafe.config.{Config, ConfigFactory}
import za.co.absa.atum.agent.AtumContext.AtumPartitions
import za.co.absa.atum.agent.dispatcher.{ConsoleDispatcher, HttpDispatcher}
import za.co.absa.atum.model.dto.{CheckpointDTO, PartitioningDTO}

/**
 * Place holder for the agent that communicate with the API.
 */
class AtumAgent private[agent] () {

  val config: Config = ConfigFactory.load()

  private val dispatcher = config.getString("atum.dispatcher.type") match {
    case "http" => new HttpDispatcher(config.getConfig("atum.dispatcher.http"))
    case "console" => new ConsoleDispatcher
    case dt => throw new UnsupportedOperationException(s"Unsupported dispatcher type: '$dt''")
  }

  /**
   * Sends `CheckpointDTO` to the AtumService API
   * @param checkpoint
   */
  def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    dispatcher.saveCheckpoint(checkpoint)
  }

  /**
   *  Provides an AtumContext given a `AtumPartitions` instance. Retrieves the data from AtumService API.
   *  @param atumPartitions
   *  @return
   */
  def getOrCreateAtumContext(atumPartitions: AtumPartitions): AtumContext = {
    val partitioningDTO = PartitioningDTO(AtumPartitions.toSeqPartitionDTO(atumPartitions), None)
    val atumContextDTO = dispatcher.getOrCreateAtumContext(partitioningDTO)
    lazy val atumContext = AtumContext.fromDTO(atumContextDTO, this)
    getExistingOrNewContext(atumPartitions, atumContext)
  }

  def getOrCreateAtumSubContext(subPartitions: AtumPartitions)(implicit parentAtumContext: AtumContext): AtumContext = {
    val newPartitions: AtumPartitions = parentAtumContext.atumPartitions ++ subPartitions

    val newPartitionsDTO = AtumPartitions.toSeqPartitionDTO(newPartitions)
    val parentPartitionsDTO = Some(AtumPartitions.toSeqPartitionDTO(parentAtumContext.atumPartitions))
    val partitioningDTO = PartitioningDTO(newPartitionsDTO, parentPartitionsDTO)

    val atumContextDTO = dispatcher.getOrCreateAtumContext(partitioningDTO)
    lazy val atumContext = AtumContext.fromDTO(atumContextDTO, this)
    getExistingOrNewContext(newPartitions, atumContext)
  }

  private def getExistingOrNewContext(atumPartitions: AtumPartitions, newAtumContext: => AtumContext): AtumContext = {
    synchronized {
      contexts.getOrElse(
        atumPartitions, {
          contexts = contexts + (atumPartitions -> newAtumContext)
          newAtumContext
        }
      )
    }
  }

  private[this] var contexts: Map[AtumPartitions, AtumContext] = Map.empty

}

object AtumAgent extends AtumAgent

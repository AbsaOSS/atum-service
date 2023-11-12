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
 * Entity that communicate with the API, primarily focused on spawning Atum Context(s).
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
   *
   * @param checkpoint Already initialized Checkpoint object to store
   */
  private [agent] def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    dispatcher.saveCheckpoint(checkpoint)
  }

  /**
   *  Provides an AtumContext given a `AtumPartitions` instance. Retrieves the data from AtumService API.
   *
   *  @param atumPartitions: Partitioning based on which an Atum Context will be created or obtained.
   *  @param authorIfNew: If partitioning doesn't exist in the store yet, a new one will be created with the authorIfNew
   *    specified in this parameter. If partitioning already exists, this attribute will be ignored because there
   *    already is an authorIfNew.
   *  @return Atum context object that's either newly created in the data store, or obtained because the input
   *          partitioning already existed.
   */
  def getOrCreateAtumContext(atumPartitions: AtumPartitions, authorIfNew: String): AtumContext = {
    val partitioningDTO = PartitioningDTO(AtumPartitions.toSeqPartitionDTO(atumPartitions), None, authorIfNew)
    val atumContextDTO = dispatcher.getOrCreateAtumContext(partitioningDTO)
    lazy val atumContext = AtumContext.fromDTO(atumContextDTO, this)
    getExistingOrNewContext(atumPartitions, atumContext)
  }

  def getOrCreateAtumSubContext(subPartitions: AtumPartitions, authorIfNew: String)
                               (implicit parentAtumContext: AtumContext): AtumContext = {
    val newPartitions: AtumPartitions = parentAtumContext.atumPartitions ++ subPartitions

    val newPartitionsDTO = AtumPartitions.toSeqPartitionDTO(newPartitions)
    val parentPartitionsDTO = Some(AtumPartitions.toSeqPartitionDTO(parentAtumContext.atumPartitions))
    val partitioningDTO = PartitioningDTO(newPartitionsDTO, parentPartitionsDTO, authorIfNew)

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

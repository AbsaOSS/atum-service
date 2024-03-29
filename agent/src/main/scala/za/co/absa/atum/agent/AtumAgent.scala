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
import za.co.absa.atum.agent.dispatcher.{CapturingDispatcher, ConsoleDispatcher, Dispatcher, HttpDispatcher}
import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, CheckpointDTO, PartitioningSubmitDTO}

/**
 *  Entity that communicate with the API, primarily focused on spawning Atum Context(s).
 */
abstract class AtumAgent private[agent] () {

  private[this] var contexts: Map[AtumPartitions, AtumContext] = Map.empty

  val dispatcher: Dispatcher

  /**
   *  Returns a user under who's security context the JVM is running.
   *  It's purpose is for auditing in author/createdBy fields.
   *
   *  Important: It's not supposed to be used for authorization as it can be spoofed!
   *
   *  @return Current user.
   */
  private[agent] def currentUser: String = System.getProperty("user.name") // platform independent

  /**
   *  Sends `CheckpointDTO` to the AtumService API
   *
   *  @param checkpoint Already initialized Checkpoint object to store
   */
  private[agent] def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    dispatcher.saveCheckpoint(checkpoint)
  }

  /**
   *  Sends the `Metadata` to the Atumservice API
   *  @param additionalData the metadata to be saved to the server.
   */
  private[agent] def saveAdditionalData(additionalData: AdditionalDataSubmitDTO): Unit = {
    dispatcher.saveAdditionalData(additionalData)
  }

  /**
   *  Provides an AtumContext given a `AtumPartitions` instance. Retrieves the data from AtumService API.
   *
   *  Note: if partitioning doesn't exist in the store yet, a new one will be created with the author stored in
   *    `AtumAgent.currentUser`. If partitioning already exists, this attribute will be ignored because there
   *    already is an author who previously created the partitioning in the data store. Each Atum Context thus
   *    can have different author potentially.
   *
   *  @param atumPartitions: Partitioning based on which an Atum Context will be created or obtained.
   *  @return Atum context object that's either newly created in the data store, or obtained because the input
   *          partitioning already existed.
   */
  def getOrCreateAtumContext(atumPartitions: AtumPartitions): AtumContext = {
    val authorIfNew = AtumAgent.currentUser
    val partitioningDTO = PartitioningSubmitDTO(AtumPartitions.toSeqPartitionDTO(atumPartitions), None, authorIfNew)

    val atumContextDTO = dispatcher.createPartitioning(partitioningDTO)
    val atumContext = AtumContext.fromDTO(atumContextDTO, this)

    getExistingOrNewContext(atumPartitions, atumContext)
  }

  /**
   *  Provides an AtumContext given a `AtumPartitions` instance for sub partitions.
   *  Retrieves the data from AtumService API.
   *  @param subPartitions Sub partitions based on which an Atum Context will be created or obtained.
   *  @param parentAtumContext Parent AtumContext.
   *  @return Atum context object
   */
  def getOrCreateAtumSubContext(subPartitions: AtumPartitions)(implicit parentAtumContext: AtumContext): AtumContext = {
    val authorIfNew = AtumAgent.currentUser
    val newPartitions: AtumPartitions = parentAtumContext.atumPartitions ++ subPartitions

    val newPartitionsDTO = AtumPartitions.toSeqPartitionDTO(newPartitions)
    val parentPartitionsDTO = Some(AtumPartitions.toSeqPartitionDTO(parentAtumContext.atumPartitions))
    val partitioningDTO = PartitioningSubmitDTO(newPartitionsDTO, parentPartitionsDTO, authorIfNew)

    val atumContextDTO = dispatcher.createPartitioning(partitioningDTO)
    val atumContext = AtumContext.fromDTO(atumContextDTO, this)

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

}

object AtumAgent extends AtumAgent {

  override val dispatcher: Dispatcher = dispatcherFromConfig()

  def dispatcherFromConfig(config: Config = ConfigFactory.load()): Dispatcher = {
    config.getString("atum.dispatcher.type") match {
      case "http" => new HttpDispatcher(config)
      case "console" => new ConsoleDispatcher(config)
      case "capture" => new CapturingDispatcher(config)
      case dt => throw new UnsupportedOperationException(s"Unsupported dispatcher type: '$dt''")
    }
  }

}

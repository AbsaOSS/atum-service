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
import za.co.absa.atum.agent.dispatcher.{CapturingDispatcher, ConsoleDispatcher, Dispatcher, HttpDispatcher}
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataPatchDTO, CheckpointDTO, PartitioningSubmitDTO}
import za.co.absa.atum.model.types.basic.AtumPartitions
import za.co.absa.atum.model.types.basic.AtumPartitionsOps

/**
 *  Entity that communicate with the API, primarily focused on spawning Atum Context(s).
 */
trait AtumAgent {

  private[this] var contexts: Map[AtumPartitions, AtumContext] = Map.empty

  val dispatcher: Dispatcher

  /**
   *  The user used for auditing in author/createdBy fields.
   *
   *  It is resolved once per agent (on first access) and then cached for the agent's lifetime:
   *    - if the `atum.author` configuration key is set (e.g. in `application.conf` or via the
   *      `-Datum.author=...` JVM system property), that value is used - this is useful when the JVM
   *      user is a generic system account (e.g. `yarn`) and the application name is more meaningful;
   *    - otherwise it falls back to the user under whose security context the JVM is running
   *      (`System.getProperty("user.name")`), which is platform independent.
   *
   *  Overriding this method replaces the resolution strategy entirely (see `AtumAgent.fromConfig`).
   *
   *  Important: It's not supposed to be used for authorization as it can be spoofed!
   */
  private[agent] def currentUser: String = resolvedCurrentUser

  // Cached, resolved-once default author identity. Ensures that agents which do not override
  // `currentUser` keep a stable audit identity for their whole lifetime, even if the Typesafe
  // Config caches are later invalidated or backing system properties change. The actual
  // configuration loading is owned by the companion object (see `AtumAgent.defaultAuthor`).
  private[this] lazy val resolvedCurrentUser: String = AtumAgent.defaultAuthor

  /**
   *  Sends `CheckpointDTO` to the AtumService API
   *
   *  @param checkpoint Already initialized Checkpoint object to store
   */
  private[agent] def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    dispatcher.saveCheckpoint(checkpoint)
  }

  /**
   *  Sends `AdditionalDataPatchDTO` to the AtumService API
   *  @param atumPartitions: Partitioning for which the additional data is to be saved.
   *  @param additionalDataPatchDTO the data to be saved or updated if already existing.
   */
  private[agent] def updateAdditionalData(
    atumPartitions: AtumPartitions,
    additionalDataPatchDTO: AdditionalDataPatchDTO
  ): AdditionalDataDTO = {
    dispatcher.updateAdditionalData(atumPartitions.toPartitioningDTO, additionalDataPatchDTO)
  }

  /**
   *  Provides an AtumContext given a `AtumPartitions` instance. Retrieves the data from AtumService API.
   *
   *  Note: if partitioning doesn't exist in the store yet, a new one will be created with the author stored in
   *    `currentUser`. If partitioning already exists, this attribute will be ignored because there
   *    already is an author who previously created the partitioning in the data store. Each Atum Context thus
   *    can have different author potentially.
   *
   *  @param atumPartitions: Partitioning based on which an Atum Context will be created or obtained.
   *  @return Atum context object that's either newly created in the data store, or obtained because the input
   *          partitioning already existed.
   */
  def getOrCreateAtumContext(atumPartitions: AtumPartitions): AtumContext = {
    val authorIfNew = this.currentUser
    val partitioningDTO = PartitioningSubmitDTO(atumPartitions.toPartitioningDTO, None, authorIfNew)

    val atumContextDTO = dispatcher.createPartitioning(partitioningDTO)
    val atumContext = AtumContext.fromDTO(atumContextDTO, this)

    getExistingOrNewContext(atumPartitions, atumContext)
  }

  /**
   *  Provides an AtumContext given a `AtumPartitions` instance for sub partitions.
   *  Retrieves the data from AtumService API.
   *
   *  @param subPartitions Sub partitions based on which an Atum Context will be created or obtained.
   *  @param mergeWithParent if true, the child partitioning is `parent ++ sub`
   *                         if false, only `sub` is used as partitioning content,
   *                         and the parent relationship is still recorded via flows.
   *
   *                         Note: If false and the child already existed, the Measures and Additional Data are not
   *                         copied over from the parent to the child, otherwise they are copied.
   *  @param parentAtumContext Parent AtumContext.
   *  @return Atum context object
   */
  def getOrCreateAtumSubContext(subPartitions: AtumPartitions)(implicit parentAtumContext: AtumContext): AtumContext =
    getOrCreateAtumSubContext(subPartitions, mergeWithParent = true)

  def getOrCreateAtumSubContext(
    subPartitions: AtumPartitions,
    mergeWithParent: Boolean = true
  )(implicit parentAtumContext: AtumContext): AtumContext = {
    val authorIfNew = this.currentUser
    val newPartitions: AtumPartitions = if (mergeWithParent) {
      parentAtumContext.atumPartitions ++ subPartitions
    } else {
      subPartitions
    }

    val newPartitionsDTO = newPartitions.toPartitioningDTO
    val parentPartitionsDTO = Some(parentAtumContext.atumPartitions.toPartitioningDTO)
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

  // `currentUser` is intentionally not overridden here: the trait default already delegates to
  // `defaultAuthor` (below) and caches the result once, which is exactly what the singleton needs.

  private[agent] def dispatcherFromConfig(config: Config = ConfigFactory.load()): Dispatcher = {
    config.getString("atum.dispatcher.type") match {
      case "http" => new HttpDispatcher(config)
      case "console" => new ConsoleDispatcher(config)
      case "capture" => new CapturingDispatcher(config)
      case dt => throw new UnsupportedOperationException(s"Unsupported dispatcher type: '$dt'")
    }
  }

  /**
   *  The default author (createdBy) identity, resolved from the globally loaded configuration.
   *  Callers cache the result (see the trait's `currentUser`), so this is evaluated once per agent.
   *
   *  @return the default author identity for agents that do not override `currentUser`.
   */
  private[agent] def defaultAuthor: String = resolveAuthor(ConfigFactory.load())

  /**
   *  Resolves the author (createdBy) identity used for auditing from the given configuration.
   *
   *  If the optional `atum.author` key is present and non-blank, its (trimmed) value is used;
   *  otherwise it falls back to `System.getProperty("user.name")`.
   *
   *  @param config configuration to read the optional `atum.author` key from.
   *  @return the resolved author identity.
   */
  private[agent] def resolveAuthor(config: Config): String = {
    if (config.hasPath("atum.author") && config.getString("atum.author").trim.nonEmpty) {
      config.getString("atum.author").trim
    } else {
      System.getProperty("user.name") // platform independent
    }
  }

  def fromConfig(config: Config): AtumAgent = new AtumAgent {
    override val dispatcher: Dispatcher = dispatcherFromConfig(config)
    override val currentUser: String = resolveAuthor(config)
  }
}

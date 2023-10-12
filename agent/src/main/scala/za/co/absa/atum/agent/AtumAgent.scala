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
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.model.dto.AtumContextDTO

/**
 * Place holder for the agent that communicate with the API.
 */
class AtumAgent private() {

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
    fetchOrGetOrCreateContext(atumPartitions)
  }

  def getOrCreateAtumSubContext(subPartitions: AtumPartitions)(implicit parentAtumContext: AtumContext): AtumContext = {
    fetchOrGetOrCreateSubContext(subPartitions)
  }

  private def fetchOrGetOrCreateContext(atumPartitions: AtumPartitions): AtumContext = {
    synchronized {
      val maybeAtumContextDTO = dispatcher.fetchAtumContext(atumPartitions)
      getOrCreateContextIfNotFetched(maybeAtumContextDTO, atumPartitions)
    }
  }

  private def fetchOrGetOrCreateSubContext(subPartitions: AtumPartitions)(implicit parentAtumContext: AtumContext): AtumContext = {
    synchronized {
      val newPartitions: AtumPartitions = parentAtumContext.atumPartitions ++ subPartitions
      val maybeAtumContextDTO = dispatcher.fetchAtumContext(newPartitions, Some(parentAtumContext.atumPartitions))
      getOrCreateSubContextIfNotFetched(maybeAtumContextDTO, newPartitions)
    }
  }

  private def getOrCreateContextIfNotFetched(maybeAtumContextDTO: Option[AtumContextDTO], atumPartitions: AtumPartitions): AtumContext = {
    maybeAtumContextDTO match {
      case Some(atumContextDTO) =>
        createContextFromDTO(atumContextDTO, atumPartitions)
      case None =>
        getOrCreateContext(atumPartitions, new AtumContext(atumPartitions, this))
    }
  }

  private def getOrCreateSubContextIfNotFetched(maybeAtumContextDTO: Option[AtumContextDTO], atumPartitions: AtumPartitions)(implicit parentAtumContext: AtumContext): AtumContext = {
    maybeAtumContextDTO match {
      case Some(atumContextDTO) =>
        createContextFromDTO(atumContextDTO, atumPartitions)
      case None =>
        getOrCreateContext(atumPartitions, parentAtumContext.copy(atumPartitions = atumPartitions, this))
    }
  }

  private def createContextFromDTO(atumContextDTO: AtumContextDTO, atumPartitions: AtumPartitions): AtumContext = {
    val atumContext = AtumContext.fromDTO(atumContextDTO, this)
    contexts = contexts + (atumPartitions -> atumContext)
    atumContext
  }

  private def getOrCreateContext(atumPartitions: AtumPartitions, creationMethod: => AtumContext): AtumContext = {
    synchronized{
      contexts.getOrElse(atumPartitions, {
        val result = creationMethod
        contexts = contexts + (atumPartitions -> result)
        result
      })
    }
  }


  private[this] var contexts: Map[AtumPartitions, AtumContext] = Map.empty

}

object AtumAgent extends AtumAgent

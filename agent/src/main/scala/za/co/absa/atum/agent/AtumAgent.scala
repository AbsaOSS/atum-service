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
import za.co.absa.atum.agent.dispacther.{ConsoleDispatcher, HttpDispatcher}
import za.co.absa.atum.agent.model.MeasureResult

/**
 * Place holder for the agent that communicate with the API.
 */
object AtumAgent {

  val config: Config = ConfigFactory.load()

  private val dispatcher = config.getString("atum.dispatcher.type") match {
    case "http" => new HttpDispatcher(config.getConfig("atum.dispatcher.http"))
    case "console" => new ConsoleDispatcher
    case dt => throw new UnsupportedOperationException(s"Unsupported dispatcher type: '$dt''")
  }

  def measurePublish(checkpointKey: String, measureResult: MeasureResult): Unit =
    dispatcher.publish(checkpointKey, measureResult)

  def publish(checkpointKey: String, context: AtumContext, measureResult: MeasureResult): Unit =
    dispatcher.publish(checkpointKey, context, measureResult)

}

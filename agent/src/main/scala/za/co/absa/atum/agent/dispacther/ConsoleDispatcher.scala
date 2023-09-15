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

package za.co.absa.atum.agent.dispacther

import com.typesafe.config.Config
import org.apache.spark.internal.Logging
import za.co.absa.atum.agent.AtumContext
import za.co.absa.atum.agent.model.MeasureResult

/**
 *  dispatcher useful for development, testing and debugging
 */
class ConsoleDispatcher extends Dispatcher with Logging {

  logInfo("using console dispatcher")

  override def publish(checkpointKey: String, measureResult: MeasureResult): Unit =
    println(s"Publishing $checkpointKey $measureResult")

  override def publish(checkpointKey: String, context: AtumContext, measureResult: MeasureResult): Unit =
    println(s"Publishing $checkpointKey $context $measureResult")

}

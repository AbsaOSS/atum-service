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

import za.co.absa.atum.agent.model.{AtumPartitions, MeasureResult}

/**
 *  Place holder for the agent that communicate with the API.
 */
object AtumAgent {

  /**
   *  Sends a single `MeasureResult` to the AtumService API along with an extra data from a given `AtumContext`.
   *  @param checkpointKey
   *  @param atumContext
   *  @param measureResult
   */
  def publish(checkpointKey: String, atumContext: AtumContext, measureResult: MeasureResult): Unit =
    println(
      s"Enqueued measurement: ${Seq(checkpointKey, atumContext, measureResult).mkString(" || ")}"
    )

  /**
   *  Sends a single `MeasureResult` to the AtumService API. It doesn't involve AtumContext.
   *
   *  @param checkpointKey
   *  @param measureResult
   */
  def measurePublish(checkpointKey: String, measureResult: MeasureResult): Unit =
    println(s"Enqueued measurement: $checkpointKey || $measureResult")

  /**
   *  Provides an AtumContext given a `AtumPartitions` instance. Retrieves the data from AtumService API.
   *  @param atumPartitions
   *  @return
   */
  def createAtumContext(atumPartitions: AtumPartitions): AtumContext = {

    /**
     *  TODO: This is a place holder
     */
    AtumContext()
  }

}

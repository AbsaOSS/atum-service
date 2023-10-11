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

import org.apache.spark.sql.DataFrame
import za.co.absa.atum.agent.model.{Measure, MeasureResult}
import AtumContext.AtumPartitions
import za.co.absa.atum.model.dto.AtumContextDTO

import scala.collection.immutable.ListMap

/**
 * This class provides the methods to measure Spark `Dataframe`. Also allows to add and remove measures.
 * @param atumPartitions
 * @param parentAgent
 * @param measures
 */
class AtumContext private[agent](
                                  val atumPartitions: AtumPartitions,
                                  val parentAgent: AtumAgent,
                                  private var measures: Set[Measure] = Set.empty) {

  def currentMeasures: Set[Measure] = measures

  def subPartitionContext(subPartitions: AtumPartitions): AtumContext = {
    parentAgent.getOrCreateAtumSubContext(atumPartitions ++ subPartitions)(this)
  }

  def createCheckpoint(checkpointName: String, dataToMeasure: DataFrame) = {
    ??? //TODO #26
  }

  def saveCheckpointMeasurements(checkpointName: String, measurements: Seq[Measure]) = {
    ??? //TODO #55
  }

  def addAdditionalData(key: String, value: String) = {
    ??? //TODO #60
  }

  def addMeasure(newMeasure: Measure): AtumContext = {
    measures = measures + newMeasure
    this
  }

  def addMeasures(newMeasures: Set[Measure]): AtumContext = {
    measures = measures ++ newMeasures
    this
  }

  def removeMeasure(measureToRemove: Measure): AtumContext = {
    measures = measures - measureToRemove
    this
  }

  private[agent] def copy(
                           atumPartitions: AtumPartitions = this.atumPartitions,
                           parentAgent: AtumAgent = this.parentAgent,
                           measures: Set[Measure] = this.measures
    ): AtumContext = {
    new AtumContext(atumPartitions, parentAgent, measures)
  }
}

object AtumContext {
  type AtumPartitions = ListMap[String, String]

  object AtumPartitions {
    def apply(elems: (String, String)): AtumPartitions = {
      ListMap(elems)
    }
  }

  private[agent] def fromDTO(atumContextDTO: AtumContextDTO, parentAgent: AtumAgent): AtumContext = {
    ???
  }

  implicit class DatasetWrapper(df: DataFrame) {

    /**
     *  Executes the measure directly (without AtumContext).
     *  @param measure the measure to be calculated
     *  @return
     */
    def executeMeasure(checkpointName: String, measure: Measure): DataFrame = {
      val result = MeasureResult(measure, measure.function(df))
      AtumAgent.measurePublish(checkpointName, result)
      df
    }

    def executeMeasures(checkpointName: String, measures: Iterable[Measure]): DataFrame = {
      measures.foreach(m => executeMeasure(checkpointName, m))
      df
    }

    /**
     *  Set a point in the pipeline to execute calculation.
     *  @param checkpointName The key assigned to this checkpoint
     *  @param atumContext Contains the calculations to be done and publish the result
     *  @return
     */
    def createCheckpoint(checkpointName: String)(implicit atumContext: AtumContext): DataFrame = {
      atumContext.measures.foreach { measure =>
        val result = MeasureResult(measure, measure.function(df))
        AtumAgent.publish(checkpointName, atumContext, result)
      }

      df
    }

  }

}

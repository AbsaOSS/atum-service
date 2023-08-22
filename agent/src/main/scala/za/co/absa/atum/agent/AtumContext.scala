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
import za.co.absa.atum.agent.model.{AtumPartitions, MeasureResult, Measurement}

/**
 *  AtumContext: This class provides the methods to measure Spark `Dataframe`. Also allows to add/edit/remove measures.
 *  @param measurements: A sequences of measurements.
 */

case class AtumContext(measurements: Set[Measurement] = Set.empty, atumPartitions: AtumPartitions = AtumPartitions()) {

  def withMeasuresReplaced(byMeasure: Measurement): AtumContext =
    this.copy(measurements = Set(byMeasure))

  def withMeasuresReplaced(byMeasures: Iterable[Measurement]): AtumContext =
    this.copy(measurements = byMeasures.toSet)

  def withMeasuresAdded(measure: Measurement): AtumContext =
    this.copy(measurements = measurements + measure)

  def withMeasuresAdded(measures: Iterable[Measurement]): AtumContext =
    this.copy(measurements = measurements ++ measures)

  def withMeasureRemoved(measurement: Measurement): AtumContext =
    this.copy(measurements = measurements.filterNot(_ == measurement))

}

object AtumContext {
  implicit class DatasetWrapper(df: DataFrame) {

    /**
     *  Executes the measure directly (without AtumContext).
     *  @param measure the measure to be calculated
     *  @return
     */
    def executeMeasure(checkpointName: String, measure: Measurement): DataFrame = {
      val result = MeasureResult(measure, measure.function(df))
      AtumAgent.measurePublish(checkpointName, result)
      df
    }

    def executeMeasures(checkpointName: String, measures: Iterable[Measurement]): DataFrame = {
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
      atumContext.measurements.foreach { measure =>
        val result = MeasureResult(measure, measure.function(df))
        AtumAgent.publish(checkpointName, atumContext, result)

        executeMeasures(checkpointName, atumContext.measurements)
      }

      df
    }

  }

}

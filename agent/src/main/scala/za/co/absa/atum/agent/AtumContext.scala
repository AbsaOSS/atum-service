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
import za.co.absa.atum.agent.model.{Measure, MeasuresMapper}
import AtumContext.AtumPartitions
import za.co.absa.atum.model.{Partition, Partitioning}
import za.co.absa.atum.model.dto.AtumContextDTO

import scala.collection.immutable.ListMap

/**
 *  This class provides the methods to measure Spark `Dataframe`. Also allows to add and remove measures.
 *  @param atumPartitions
 *  @param agent
 *  @param measures
 */
class AtumContext private[agent] (
  val atumPartitions: AtumPartitions,
  val agent: AtumAgent,
  private var measures: Set[Measure] = Set.empty
) {

  def currentMeasures: Set[Measure] = measures

  def subPartitionContext(subPartitions: AtumPartitions): AtumContext = {
    agent.getOrCreateAtumSubContext(atumPartitions ++ subPartitions)(this)
  }

  def createCheckpoint(checkpointName: String, author: String, dataToMeasure: DataFrame) = {
    ??? // TODO #26
  }

  def saveCheckpointMeasurements(checkpointName: String, measurements: Seq[Measure]) = {
    ??? // TODO #55
  }

  def addAdditionalData(key: String, value: String) = {
    ??? // TODO #60
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
    agent: AtumAgent = this.agent,
    measures: Set[Measure] = this.measures
  ): AtumContext = {
    new AtumContext(atumPartitions, agent, measures)
  }
}

object AtumContext {
  type AtumPartitions = ListMap[String, String]

  object AtumPartitions {
    def apply(elems: (String, String)): AtumPartitions = {
      ListMap(elems)
    }

    def apply(elems: Seq[(String, String)]): AtumPartitions = {
      ListMap(elems:_*)
    }

    private[agent] def toPartitioning(atumPartitions: AtumPartitions): Partitioning = {
      Partitioning(atumPartitions.toSeq.map{ case (key, value) => Partition(key, value) })
    }

    private[agent] def fromPartitioning(partitioning: Partitioning): AtumPartitions = {
      AtumPartitions(partitioning.partitioning.map(partition => partition.key -> partition.value))
    }
  }

  private[agent] def fromDTO(atumContextDTO: AtumContextDTO, agent: AtumAgent): AtumContext = {
    new AtumContext(
      AtumPartitions.fromPartitioning(atumContextDTO.partitioning),
      agent,
      MeasuresMapper.mapToMeasures(atumContextDTO.measures)
    )
  }

  implicit class DatasetWrapper(df: DataFrame) {

    /**
     *  Set a point in the pipeline to execute calculation.
     *  @param checkpointName The key assigned to this checkpoint
     *  @param atumContext Contains the calculations to be done and publish the result
     *  @return
     */
    def createCheckpoint(checkpointName: String, author: String)(implicit atumContext: AtumContext): DataFrame = {
      // todo: implement checkpoint creation
      df
    }

  }

}

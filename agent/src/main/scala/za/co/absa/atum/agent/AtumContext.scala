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
import za.co.absa.atum.agent.AtumContext.AtumPartitions
import za.co.absa.atum.agent.model.Measurement.MeasurementByAtum
import za.co.absa.atum.agent.model._
import za.co.absa.atum.model.dto._

import java.time.ZonedDateTime

import java.util.UUID
import scala.collection.immutable.ListMap

/**
 *  This class provides the methods to measure Spark `Dataframe`. Also allows to add and remove measures.
 *  @param atumPartitions: Atum partitions associated with a given Atum Context.
 *  @param agent: Reference to an Atum Agent object that will be used within the current Atum Contedt.
 *  @param measures: Variable set of measures associated with a given partitions / context.
 *  @param additionalData: Additional metadata or tags, associated with a given context.
 */
class AtumContext private[agent] (
  val atumPartitions: AtumPartitions,
  val agent: AtumAgent,
  private var measures: Set[Measure] = Set.empty,
  private var additionalData: Map[String, Option[String]] = Map.empty
) {

  /**
   * Returns the current set of measures in the AtumContext.
   *
   * @return the current set of measures
   */
  def currentMeasures: Set[Measure] = measures

  /**
   * Returns the sub-partition context in the AtumContext.
   *
   * @return the sub-partition context
   */
  def subPartitionContext(subPartitions: AtumPartitions): AtumContext = {
    agent.getOrCreateAtumSubContext(atumPartitions ++ subPartitions)(this)
  }

  private def takeMeasurements(df: DataFrame): Set[MeasurementByAtum] = {
    measures.map { m =>
      val measurementResult = m.function(df)
      MeasurementByAtum(m, measurementResult.result, measurementResult.resultType)
    }
  }

  /**
   * Creates a checkpoint in the AtumContext.
   *
   * This method is used to mark a specific point in the data processing pipeline where measurements of data
   * completeness are taken.
   * The checkpoint is identified by a name, which can be used later to retrieve the measurements taken at this point.
   * After the checkpoint is created, the method returns the AtumContext for further operations.
   *
   * @param checkpointName the name of the checkpoint to be created. This name should be descriptive of the point in
   *                       the data processing pipeline where the checkpoint is created.
   * @return the AtumContext after the checkpoint has been created.
   *         This allows for method chaining in the data processing pipeline.
   */
  def createCheckpoint(checkpointName: String, dataToMeasure: DataFrame): AtumContext = {
    val startTime = ZonedDateTime.now()
    val measurements = takeMeasurements(dataToMeasure)
    val endTime = ZonedDateTime.now()

    val checkpointDTO = CheckpointDTO(
      id = UUID.randomUUID(),
      name = checkpointName,
      author = this.agent.currentUser,
      measuredByAtumAgent = true,
      partitioning = AtumPartitions.toSeqPartitionDTO(this.atumPartitions),
      processStartTime = startTime,
      processEndTime = Some(endTime),
      measurements = measurements.map(MeasurementBuilder.buildMeasurementDTO).toSeq
    )

    agent.saveCheckpoint(checkpointDTO)
    this
  }

  /**
   * Creates a checkpoint with the specified name and provided measurements.
   *
   * @param checkpointName the name of the checkpoint to be created
   * @param measurements   the measurements to be included in the checkpoint
   * @return the AtumContext after the checkpoint has been created
   */
  def createCheckpointOnProvidedData(checkpointName: String, measurements: Seq[Measurement]): AtumContext = {
    val dateTimeNow = ZonedDateTime.now()

    val checkpointDTO = CheckpointDTO(
      id = UUID.randomUUID(),
      name = checkpointName,
      author = this.agent.currentUser,
      partitioning = AtumPartitions.toSeqPartitionDTO(this.atumPartitions),
      processStartTime = dateTimeNow,
      processEndTime = Some(dateTimeNow),
      measurements = measurements.map(MeasurementBuilder.buildMeasurementDTO)
    )

    agent.saveCheckpoint(checkpointDTO)
    this
  }

  /**
   * This method creates `Metadata` in the agentService.
   * @param key The placeholder for the added additional data.
   * @param value The actual additional data added.
   * @return
   */
  def createMetadata(): Unit = {
//
//    val metadata = MetadataDTO(
//      this.atumPartitions,
//      additionalData
//    )
    val additionalData = AdditionalDataDTO(AtumPartitions.toSeqPartitionDTO(this.atumPartitions), this.additionalData)

//    Dispatcher.saveAdditionalData(additionalData)

  }

  /**
   * Adds additional data to the AtumContext.
   *
   * @param key   the key of the additional data
   * @param value the value of the additional data
   */
  def addAdditionalData(key: String, value: String): Unit = {
    additionalData += (key -> Some(value))
  }

  /**
   * Returns the current additional data in the AtumContext.
   *
   * @return the current additional data
   */
  def currentAdditionalData: Map[String, Option[String]] = {
    this.additionalData
  }

  /**
   * Adds a measure to the AtumContext.
   *
   * @param measure the measure to be added
   */
  def addMeasure(newMeasure: Measure): AtumContext = {
    measures = measures + newMeasure
    this
  }

  /**
   * Adds multiple measures to the AtumContext.
   *
   * @param measures set sequence of measures to be added
   */
  def addMeasures(newMeasures: Set[Measure]): AtumContext = {
    measures = measures ++ newMeasures
    this
  }

  /**
   * Removes a measure from the AtumContext.
   *
   * @param measureToRemove the measure to be removed
   */
  def removeMeasure(measureToRemove: Measure): AtumContext = {
    measures = measures - measureToRemove
    this
  }

  private[agent] def copy(
    atumPartitions: AtumPartitions = this.atumPartitions,
    agent: AtumAgent = this.agent,
    measures: Set[Measure] = this.measures,
    additionalData: Map[String, Option[String]] = this.additionalData
  ): AtumContext = {
    new AtumContext(atumPartitions, agent, measures, additionalData)
  }
}

object AtumContext {
  /**
   * Type alias for Atum partitions.
   */
  type AtumPartitions = ListMap[String, String]

  /**
   * Object contains helper methods to work with Atum partitions.
   */
  object AtumPartitions {
    def apply(elems: (String, String)): AtumPartitions = {
      ListMap(elems)
    }

    def apply(elems: List[(String, String)]): AtumPartitions = {
      ListMap(elems:_*)
    }

    private[agent] def toSeqPartitionDTO(atumPartitions: AtumPartitions): PartitioningDTO = {
      atumPartitions.map { case (key, value) => PartitionDTO(key, value) }.toSeq
    }

    private[agent] def fromPartitioning(partitioning: PartitioningDTO): AtumPartitions = {
      AtumPartitions(partitioning.map(partition => Tuple2(partition.key, partition.value)).toList)
    }
  }

  private[agent] def fromDTO(atumContextDTO: AtumContextDTO, agent: AtumAgent): AtumContext = {
    new AtumContext(
      AtumPartitions.fromPartitioning(atumContextDTO.partitioning),
      agent,
      MeasuresBuilder.mapToMeasures(atumContextDTO.measures),
      atumContextDTO.additionalData.additionalData
    )
  }

  /**
   * Implicit class to add a method to DataFrame to create a checkpoint.
   */
  implicit class DatasetWrapper(df: DataFrame) {

    /**
     *  Set a point in the pipeline to execute calculation and store it.
     *  @param checkpointName The key assigned to this checkpoint
     *  @param atumContext Contains the calculations to be done and publish the result
     *  @return
     */
    def createCheckpoint(checkpointName: String)(implicit atumContext: AtumContext): DataFrame = {
      atumContext.createCheckpoint(checkpointName, df)
      df
    }

  }

}

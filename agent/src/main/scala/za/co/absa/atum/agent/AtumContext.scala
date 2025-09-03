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
import za.co.absa.atum.agent.exception.AtumAgentException.PartitioningUpdateException
import za.co.absa.atum.agent.model._
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.types.basic.{AdditionalData, AtumPartitions, AtumPartitionsOps, PartitioningDTOOps}

import java.time.ZonedDateTime
import java.util.UUID

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
  private var measures: Set[AtumMeasure] = Set.empty,
  private var additionalData: AdditionalData = Map.empty
) {

  /**
   * Returns the current set of measures in the AtumContext.
   *
   * @return the current set of measures
   */
  def currentMeasures: Set[AtumMeasure] = measures

  /**
   * Returns the sub-partition context in the AtumContext.
   *
   * @return the sub-partition context
   */
  def subPartitionContext(subPartitions: AtumPartitions): AtumContext = {
    val overlap = atumPartitions.keys.toSet.intersect(subPartitions.keys.toSet)
    if (overlap.nonEmpty) {
      throw PartitioningUpdateException(s"Partition keys '$overlap' already exist. Updates are not allowed.")
    }
    agent.getOrCreateAtumSubContext(atumPartitions ++ subPartitions)(this)
  }

  private def takeMeasurements(df: DataFrame): Set[MeasurementDTO] = {
    measures.map { m =>
      // TODO group measurements together: https://github.com/AbsaOSS/atum-service/issues/98
      val measureResult = m.function(df)
      MeasurementBuilder.buildMeasurementDTO(m, measureResult)
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
    val measurementDTOs = takeMeasurements(dataToMeasure)
    val endTime = ZonedDateTime.now()

    val checkpointDTO = CheckpointDTO(
      id = UUID.randomUUID(),
      name = checkpointName,
      author = agent.currentUser,
      measuredByAtumAgent = true,
      partitioning = atumPartitions.toPartitioningDTO,
      processStartTime = startTime,
      processEndTime = Some(endTime),
      measurements = measurementDTOs
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
  def createCheckpointOnProvidedData(checkpointName: String, measurements: Map[Measure, MeasureResult]): AtumContext = {
    val dateTimeNow = ZonedDateTime.now()

    val checkpointDTO = CheckpointDTO(
      id = UUID.randomUUID(),
      name = checkpointName,
      author = agent.currentUser,
      partitioning = atumPartitions.toPartitioningDTO,
      processStartTime = dateTimeNow,
      processEndTime = Some(dateTimeNow),
      measurements = MeasurementBuilder.buildAndValidateMeasurementsDTO(measurements)
    )

    agent.saveCheckpoint(checkpointDTO)
    this
  }

  /**
   * Adds additional data to the AtumContext.
   *
   * @param key   the key of the additional data
   * @param value the value of the additional data
   *
   * @return the AtumContext after the AD has been dispatched and added
   */
  def addAdditionalData(key: String, value: String): AtumContext = {
    addAdditionalData(Map(key -> value))
  }

  /**
   * This method creates Additional Data in the agentService and dispatches them into the data store.
   *
   * @param newAdditionalDataToAdd additional data that will be added into the data store
   *
   * @return the AtumContext after the AD has been dispatched and added
   */
  def addAdditionalData(newAdditionalDataToAdd: Map[String, String]): AtumContext = {
    val currAdditionalDataSubmit = AdditionalDataPatchDTO(
      agent.currentUser,
      newAdditionalDataToAdd,
    )
    val retrievedAD = agent.updateAdditionalData(this.atumPartitions, currAdditionalDataSubmit)

    // Could be different from the one that was submitted. Replacing, just to have the most fresh copy possible.
    this.additionalData = retrievedAD.data.map{ case (k, v) => (k, v.map(_.value)) }
    this
  }

  /**
   * Returns the current additional data in the AtumContext.
   *
   * @return the current additional data
   */
  def currentAdditionalData: AdditionalData = {
    additionalData
  }

  /**
   * Adds a measure to the AtumContext.
   *
   * @param newMeasure the measure to be added
   */
  def addMeasure(newMeasure: AtumMeasure): AtumContext = {
    measures = measures + newMeasure
    this
  }

  /**
   * Adds multiple measures to the AtumContext.
   *
   * @param newMeasures set sequence of measures to be added
   */
  def addMeasures(newMeasures: Set[AtumMeasure]): AtumContext = {
    measures = measures ++ newMeasures
    this
  }

  /**
   * Removes a measure from the AtumContext.
   *
   * @param measureToRemove the measure to be removed
   */
  def removeMeasure(measureToRemove: AtumMeasure): AtumContext = {
    measures = measures - measureToRemove
    this
  }

  private[agent] def copy(
    atumPartitions: AtumPartitions = atumPartitions,
    agent: AtumAgent = agent,
    measures: Set[AtumMeasure] = measures,
    additionalData: AdditionalData = additionalData
  ): AtumContext = {
    new AtumContext(atumPartitions, agent, measures, additionalData)
  }
}

object AtumContext {

  private[agent] def fromDTO(atumContextDTO: AtumContextDTO, agent: AtumAgent): AtumContext = {
    new AtumContext(
      atumContextDTO.partitioning.toAtumPartitions,
      agent,
      MeasuresBuilder.mapToMeasures(atumContextDTO.measures),
      atumContextDTO.additionalData
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

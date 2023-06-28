package za.co.absa.atum.agent.model

import za.co.absa.atum.agent.model.AtumPartitions.Partitions

import java.time.LocalDate
import scala.collection.immutable.ListMap

case class AtumPartitions(name: String, reportDate: LocalDate, metadata: Partitions) {

  /**
   *  Creates a new Segmentation instance with the given metadata
   *  @param metadata metadata key value map that preserves the order of arrival of the elements.
   *  @return
   */
  def withPartitions(metadata: Partitions): AtumPartitions = this.copy(metadata = metadata)

  /**
   *  Creates a new Segmentation instance with new metadata key values added known as a sub-segmentation
   *  @param metadata metadata key value map to be added to the existing metadata
   *  @return
   */
  def addPartitions(metadata: Partitions): AtumPartitions = this.copy(metadata = this.metadata ++ metadata)

  /**
   *  An alias for `addPartitions`
   */
  def subPartitions(metadata: Partitions): AtumPartitions = addPartitions(metadata)

}

object AtumPartitions {

  // Each element represent a data partition. The order is preserved as a list.
  type Partitions = ListMap[String, String]
}

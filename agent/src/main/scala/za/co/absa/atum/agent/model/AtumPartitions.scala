package za.co.absa.atum.agent.model

import za.co.absa.atum.agent.model.AtumPartitions.Partitions

import java.time.LocalDate
import scala.collection.immutable.ListMap

case class AtumPartitions(name: String, reportDate: LocalDate, partitions: Partitions) {

  /**
   *  Creates a new Partition instance with the given metadata
   *  @param partitions metadata key value map that preserves the order of arrival of the elements.
   *  @return
   */
  def withPartitions(partitions: Partitions): AtumPartitions = this.copy(partitions = partitions)

  /**
   *  Creates a new Partition instance with new metadata key values added known as a sub-Partition
   *  @param partitions metadata key value map to be added to the existing metadata
   *  @return
   */
  def addPartitions(partitions: Partitions): AtumPartitions = this.copy(partitions = this.partitions ++ partitions)

  /**
   *  Creates a new Partition instance with new metadata key values added known as a sub-Partition
   *
   *  @param partition metadata key value map to be added to the existing metadata
   *  @return
   */
  def addPartition(partition: Map[String, String]): AtumPartitions =
    this.copy(partitions = this.partitions ++ partition)

  /**
   *  Creates a new Partition instance with new partition key values added known as a sub-Partition
   *  @param key new partition key
   *  @param value new partition value
   *  @return
   */
  def addPartition(key: String, value: String): AtumPartitions =
    this.copy(partitions = this.partitions + (key -> value))

  /**
   *  An alias for `addPartitions`
   */
  def subPartitions(partitions: Partitions): AtumPartitions = addPartitions(partitions)

}

object AtumPartitions {

  // Each element represent a data partition. The order is preserved as a list.
  type Partitions = ListMap[String, String]
}

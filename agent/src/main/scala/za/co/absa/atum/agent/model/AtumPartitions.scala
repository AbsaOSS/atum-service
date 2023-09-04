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

package za.co.absa.atum.agent.model

import za.co.absa.atum.agent.model.AtumPartitionsOld.Partitions

import scala.collection.immutable.ListMap

case class AtumPartitionsOld(partitions: Partitions = ListMap()) {

  /**
   *  Creates a new `AtumPartitions` instance with the given metadata
   *  @param partitions metadata key value map that preserves the order of arrival of the elements.
   *  @return
   */
  def withPartitions(partitions: Partitions): AtumPartitionsOld = this.copy(partitions = partitions)

  /**
   *  Creates a new `AtumPartitions` instance with new metadata key values added known as a sub-Partition
   *  @param partitions metadata key value map to be added to the existing metadata
   *  @return
   */
  def addPartitions(partitions: Partitions): AtumPartitionsOld = this.copy(partitions = this.partitions ++ partitions)

  /**
   *  Creates a new Partition instance with new metadata key values added known as a sub-Partition
   *  @param partitions key value map to be added to the existing metadata
   *  @return
   */
  def addPartitions(partitions: Map[String, String]): AtumPartitionsOld = {
    val typedPartitions: Partitions = ListMap(partitions.toList: _*)
    addPartitions(typedPartitions)
  }

  /**
   *  Creates a new Partition instance with new partition key values added known as a sub-Partition
   *  @param key new partition key
   *  @param value new partition value
   *  @return
   */
  def addPartition(key: String, value: String): AtumPartitionsOld =
    this.copy(partitions = this.partitions + (key -> value))

  /**
   *  An alias for `addPartitions`
   */
  def subPartitions(partitions: Partitions): AtumPartitionsOld = addPartitions(partitions)

}

object AtumPartitionsOld {

  // Each element represent a data partition. The order is preserved as a list.
  type Partitions = ListMap[String, String]
}

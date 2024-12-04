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

package za.co.absa.atum.model.types

import scala.collection.immutable.ListMap
import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningDTO}


object basic {
  /**
   * Type alias for Atum partitions.
   */
  type AtumPartitions = ListMap[String, String]
  type AdditionalData = Map[String, Option[String]]

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

  }

  implicit class AtumPartitionsOps(val atumPartitions: AtumPartitions) extends AnyVal {
    def toPartitioningDTO: PartitioningDTO = {
      atumPartitions.map { case (key, value) => PartitionDTO(key, value) }.toSeq
    }
  }

  implicit class PartitioningDTOOps(val partitioning: PartitioningDTO) extends AnyVal {
    def toAtumPartitions: AtumPartitions = {
      AtumPartitions(partitioning.map(partition => Tuple2(partition.key, partition.value)).toList)
    }
  }

}

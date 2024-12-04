/*
 * Copyright 2024 ABSA Group Limited
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

import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.model.dto.PartitionDTO
import za.co.absa.atum.model.types.basic.AtumPartitions

import scala.collection.immutable.ListMap



class AtumPartitionsUnitTests extends AnyFunSuiteLike {
  test("Creating AtumPartitions from one pair of key-value") {
    val expected = ListMap("a" -> "b")
    val result = AtumPartitions(("a", "b"))
    assert(result == expected)
  }

  test("Creating AtumPartitions from multiple key-value pairs") {
    val expected = ListMap(
      "a" -> "b",
      "e" -> "Hello",
      "c" -> "d"
    )
    val result = AtumPartitions(List(
      ("a", "b"),
      ("e", "Hello"),
      ("c", "d")
    ))
    assert(result == expected)
    assert(result.head == ("a", "b"))
  }

  test("Conversion to PartitioningDTO returns expected result") {
    import za.co.absa.atum.model.types.basic.AtumPartitionsOps

    val atumPartitions = AtumPartitions(List(
      ("a", "b"),
      ("e", "Hello"),
      ("c", "d")
    ))

    val expected = Seq(
      PartitionDTO("a", "b"),
      PartitionDTO("e", "Hello"),
      PartitionDTO("c", "d")
    )

    assert(atumPartitions.toPartitioningDTO == expected)
  }

  test("Creating AtumPartitions from PartitioningDTO") {
    import za.co.absa.atum.model.types.basic.PartitioningDTOOps

    val partitionDTO = Seq(
      PartitionDTO("a", "b"),
      PartitionDTO("e", "Hello"),
      PartitionDTO("c", "d")
    )

    val expected = AtumPartitions(List(
      ("a", "b"),
      ("e", "Hello"),
      ("c", "d")
    ))

    assert(partitionDTO.toAtumPartitions == expected)
  }

}

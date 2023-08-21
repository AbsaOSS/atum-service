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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.model.AtumPartitions.Partitions

import scala.collection.immutable.ListMap

class AtumPartitionsSpec extends AnyFlatSpec with Matchers {

  "withPartitions" should "replace or create partitions if not exists" in {

    val atumPartitions = AtumPartitions()
    val partitions: Partitions = ListMap("country" -> "South Africa", "gender" -> "female")

    val result = atumPartitions
      .withPartitions(partitions)

    assert(result.partitions.head == "country" -> "South Africa")
    assert(result.partitions.tail.head == "gender" -> "female")

    val result2 = result.withPartitions(ListMap("a" -> "a", "b" -> "b"))

    assert(result2.partitions.head == "a" -> "a")
    assert(result2.partitions.tail.head == "b" -> "b")

  }

  "addPartitions" should "add more partitions preserving the order" in {

    val atumPartitions = AtumPartitions(ListMap("pA" -> "a", "pB" -> "b"))

    val result = atumPartitions
      .addPartition("pC", "c")

    assert(result.partitions.head == "pA" -> "a")
    assert(result.partitions.tail.tail.head == "pC" -> "c")

    val result1 = AtumPartitions(ListMap("pA" -> "a", "pB" -> "b"))
      .addPartitions(Map("pA" -> "last"))

    assert(result1.partitions.head == "pB" -> "b")
    assert(result1.partitions.tail.head == "pA" -> "last") // now pA is the last changed

  }
}

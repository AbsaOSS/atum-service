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

import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.model.dto.{PartitioningDTO, PartitionDTO}

class PartitioningCreateRequestUnitTests extends AnyFunSuiteLike {

  private val partitioning: PartitioningDTO = Seq(PartitionDTO("key", "value"))
  private val parent: PartitioningDTO = Seq(PartitionDTO("parentKey", "parentValue"))

  test("Single-parent convenience factory wraps a defined parent into a one-element Seq") {
    val request = PartitioningCreateRequest(partitioning, Some(parent), "author")
    assert(request == PartitioningCreateRequest(partitioning, Seq(parent), "author", copyMeasureDefinitions = false))
  }

  test("Single-parent convenience factory produces an empty Seq when there is no parent") {
    val request = PartitioningCreateRequest(partitioning, None, "author")
    assert(request == PartitioningCreateRequest(partitioning, Seq.empty, "author", copyMeasureDefinitions = false))
  }

  test("Primary constructor defaults copyMeasureDefinitions to false") {
    val request = PartitioningCreateRequest(partitioning, Seq(parent), "author")
    assert(!request.copyMeasureDefinitions)
  }

  test("Primary constructor honours an explicit copyMeasureDefinitions flag") {
    val request = PartitioningCreateRequest(partitioning, Seq(parent), "author", copyMeasureDefinitions = true)
    assert(request.copyMeasureDefinitions)
  }

}

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

package za.co.absa.atum.server.model

import za.co.absa.atum.model.dto.PartitioningDTO

case class PartitioningForDB private(
  version: Int = 1,
  keys: Seq[String],
  keysToValuesMap: Map[String, String]
)

object PartitioningForDB {

  def fromSeqPartitionDTO(partitioning: PartitioningDTO): PartitioningForDB = {
    val allKeys = partitioning.map(_.key)
    val mapOfKeysAndValues = partitioning.map(p => p.key -> p.value).toMap[String, String]

    PartitioningForDB(keys = allKeys, keysToValuesMap = mapOfKeysAndValues)
  }
}


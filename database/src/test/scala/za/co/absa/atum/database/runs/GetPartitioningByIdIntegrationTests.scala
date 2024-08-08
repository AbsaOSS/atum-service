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

package za.co.absa.atum.database.runs

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString

class GetPartitioningByIdIntegrationTests extends DBTestSuite {

  private val fncGetPartitioningById = "runs.get_partitioning_by_id"

  private val partitioning1 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyX", "keyY", "keyZ"],
      |   "keysToValues": {
      |     "keyX": "value1",
      |     "keyY": "value2",
      |     "keyZ": "value3"
      |   }
      |}
      |""".stripMargin
  )

  private val partitioning2 = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3", "key2", "key4"],
      |  "keysToValues": {
      |    "key1": "valueX",
      |    "key2": "valueY",
      |    "key3": "valueZ",
      |    "key4": "valueA"
      |  }
      |}
      |""".stripMargin
  )

  private val expectedPartitioning1 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyX", "keyY", "keyZ"],
      |   "keysToValues": {
      |     "keyX": "value1",
      |     "keyZ": "value3",
      |     "keyY": "value2"
      |   }
      |}
      |""".stripMargin
  )

  test("Partitioning retrieved successfully") {
    table("runs.partitionings").insert(
      add("partitioning", partitioning1)
        .add("created_by", "Joseph")
    )

    table("runs.partitionings").insert(
      add("partitioning", partitioning2)
        .add("created_by", "Daniel")
    )

    val fkPartitioning1: Long = table("runs.partitionings").fieldValue("partitioning", partitioning1, "id_partitioning").get.get
    val fkPartitioning2: Long = table("runs.partitionings").fieldValue("partitioning", partitioning2, "id_partitioning").get.get

    val result = function(fncGetPartitioningById)
      .setParam("i_id", fkPartitioning1)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("id").contains(fkPartitioning1))
        assert(row.getJsonB("partitioning").contains(partitioning1))
        assert(row.getJsonB("parent_partitioning").isDefined)
        assert(row.getString("author").contains("Joseph"))
      }

    table("runs.partitionings").where(add("id_partitioning", fkPartitioning1)) { partitioningResult =>
      val row = partitioningResult.next()
      assert(row.getJsonB("partitioning").contains(expectedPartitioning1))
      assert(row.getString("created_by").contains("Joseph"))
    }
  }

  test("Partitioning not found") {
    val nonExistentID = 9999L

    val result = function(fncGetPartitioningById)
      .setParam("i_id", nonExistentID)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Partitioning not found"))
        assert(row.getJsonB("partitioning").isEmpty)
        assert(row.getJsonB("parent_partitioning").isEmpty)
        assert(row.getString("author").isEmpty)
      }
  }
}



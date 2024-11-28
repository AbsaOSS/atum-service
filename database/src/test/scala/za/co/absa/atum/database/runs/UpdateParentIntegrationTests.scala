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

class UpdateParentIntegrationTests extends DBTestSuite {

  private val updateParentFn = "runs.patch_partitioning_parent"
  private val createPartitioningFn = "runs.create_partitioning"

  private val partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3", "key2", "key4"],
      |  "keysToValuesMap": {
      |    "key1": "valueX",
      |    "key2": "valueY",
      |    "key3": "valueZ",
      |    "key4": "valueA"
      |  }
      |}
      |""".stripMargin
  )

  private val partitioning2 = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3", "key2", "key4"],
      |  "keysToValuesMap": {
      |    "key1": "valueA",
      |    "key2": "valueB",
      |    "key3": "valueC",
      |    "key4": "valueD"
      |  }
      |}
      |""".stripMargin
  )

  private val parentPartitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3"],
      |  "keysToValuesMap": {
      |    "key1": "valueX",
      |    "key3": "valueZ"
      |  }
      |}
      |""".stripMargin
  )

  private val parentPartitioning2 = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3"],
      |  "keysToValuesMap": {
      |    "key1": "valueW",
      |    "key3": "valueY"
      |  }
      |}
      |""".stripMargin
  )

  test("Child Partitioning not found") {
    val nonExistentID = 9999L

    val parentPartitioningID = function(createPartitioningFn)
      .setParam("i_partitioning", parentPartitioning)
      .setParam("i_by_user", "Albert Einstein")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    function(updateParentFn)
      .setParam("i_partitioning_id", nonExistentID)
      .setParam("i_parent_id", parentPartitioningID)
      .setParam("i_by_user", "Fantômas")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Child Partitioning not found"))
        assert(row.getJsonB("parent_id").isEmpty)
        assert(!queryResult.hasNext)
      }

  }

  test("Parent Partitioning not found") {
    val nonExistentID = 9999L

    val parentPartitioningID = function(createPartitioningFn)
      .setParam("i_partitioning", parentPartitioning)
      .setParam("i_by_user", "Albert Einstein")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    val partitioningID = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_parent_partitioning_id", parentPartitioningID)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(12))
        assert(row.getString("status_text").contains("Partitioning created with parent partitioning"))
        row.getLong("id_partitioning").get
      }

    function(updateParentFn)
      .setParam("i_partitioning_id", partitioningID)
      .setParam("i_parent_id", nonExistentID)
      .setParam("i_by_user", "Fantômas")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Parent Partitioning not found"))
        assert(row.getJsonB("parent_id").isEmpty)
        assert(!queryResult.hasNext)
      }

  }

  test("Parent Partitioning Updated") {

    val parentPartitioningID = function(createPartitioningFn)
      .setParam("i_partitioning", parentPartitioning)
      .setParam("i_by_user", "Albert Einstein")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    val parentPartitioningID2 = function(createPartitioningFn)
      .setParam("i_partitioning", parentPartitioning2)
      .setParam("i_by_user", "Tomas Riddle")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    val partitioningID = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_parent_partitioning_id", parentPartitioningID)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(12))
        assert(row.getString("status_text").contains("Partitioning created with parent partitioning"))
        row.getLong("id_partitioning").get
      }

    function(updateParentFn)
      .setParam("i_partitioning_id", partitioningID)
      .setParam("i_parent_id", parentPartitioningID2)
      .setParam("i_by_user", "Happy Nappy")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Parent Updated"))
        assert(row.getLong("parent_id").contains(parentPartitioningID2))
        assert(!queryResult.hasNext)
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningID)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningID2)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID)) == 2
    )

    function(updateParentFn)
      .setParam("i_partitioning_id", partitioningID)
      .setParam("i_parent_id", parentPartitioningID)
      .setParam("i_by_user", "Happy Nappy")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Parent Updated"))
        assert(row.getLong("parent_id").contains(parentPartitioningID))
        assert(!queryResult.hasNext)
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningID)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningID2)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID)) == 2
    )

    var partitioningID2 = 0L;

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID2)) == 0
    )

    partitioningID2 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning2)
      .setParam("i_by_user", "Tomas Riddler")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID2)) == 1
    )

    function(updateParentFn)
      .setParam("i_partitioning_id", partitioningID2)
      .setParam("i_parent_id", parentPartitioningID)
      .setParam("i_by_user", "Happy Nappy")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Parent Updated"))
        assert(row.getLong("parent_id").contains(parentPartitioningID))
        assert(!queryResult.hasNext)
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningID)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningID2)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID)) == 2
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID2)) == 2
    )

  }
}

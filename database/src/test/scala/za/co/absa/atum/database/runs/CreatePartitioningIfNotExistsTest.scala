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

class CreatePartitioningIfNotExistsTest extends DBTestSuite{

  private val fncCreatePartitioningIfNotExists = "runs.create_partitioning_if_not_exists"

  private val partitioning = JsonBString(
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

  private val parentPartitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3"],
      |  "keysToValues": {
      |    "key1": "valueX",
      |    "key3": "valueZ"
      |  }
      |}
      |""".stripMargin
  )

  test("Partitioning created") {
    val partitioningID = function(fncCreatePartitioningIfNotExists)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    table("runs.partitionings").where(add("id_partitioning", partitioningID)) {partitioningResult =>
      val row = partitioningResult.next()
      // assert(row.getJsonB("partitioning").contains(partitioning)) TODO keys are reordered in JsonB and whitespaces removed
      assert(row.getString("created_by").contains("Fantômas"))
      assert(row.getOffsetDateTime("created_at").contains(now()))
    }

    val idFlow = table("flows.partitioning_to_flow").where(add("fk_partitioning", partitioningID)) { partToFlowResult =>
      assert(partToFlowResult.hasNext)
      val partToFlowRow = partToFlowResult.next()
      val result = partToFlowRow.getLong("fk_flow")
      assert(partToFlowRow.getString("created_by").contains("Fantômas"))
      assert(!partToFlowResult.hasNext)
      result.get
    }

    table("flows.flows").where(add("id_flow", idFlow)) {flowsResult =>
      assert(flowsResult.hasNext)
      val flowRow = flowsResult.next()
      assert(flowRow.getString("flow_name").exists(_.startsWith("Custom flow #")))
      assert(flowRow.getString("flow_description").contains(""))
      assert(flowRow.getBoolean("from_pattern").contains(false))
      assert(flowRow.getString("created_by").contains("Fantômas"))
      assert(flowRow.getOffsetDateTime("created_at").contains(now()))
      assert(!flowsResult.hasNext)
    }
  }
  test("Partitioning created with parent partitioning that already exists") {
    val parentPartitioningID = function(fncCreatePartitioningIfNotExists)
      .setParam("i_partitioning", parentPartitioning)
      .setParam("i_by_user", "Albert Einstein")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningID)) == 1
    )
    val partitioningID = function(fncCreatePartitioningIfNotExists)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_parent_partitioning", parentPartitioning)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningID)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID)) == 2
    )
  }

  test("Partitioning already exists") {
    val partitioningID = function(fncCreatePartitioningIfNotExists)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    function(fncCreatePartitioningIfNotExists)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(14))
        assert(row.getString("status_text").contains("Partitioning already present"))
        assert(row.getLong("id_partitioning").contains(partitioningID))
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID)) == 1
    )
  }

  test("Partitioning exists, but parent is added") {
    val partitioningID = function(fncCreatePartitioningIfNotExists)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID)) == 1
    )

    function(fncCreatePartitioningIfNotExists)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_parent_partitioning", parentPartitioning)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(12))
        assert(row.getString("status_text").contains("Partitioning parent registered"))
        assert(row.getLong("id_partitioning").contains(partitioningID))
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", partitioningID)) == 2
    )
  }
}

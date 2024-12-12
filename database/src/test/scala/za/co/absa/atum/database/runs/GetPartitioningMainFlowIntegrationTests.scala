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

class GetPartitioningMainFlowIntegrationTests extends DBTestSuite {

  private val fncGetPartitioningMainFlow = "runs.get_partitioning_main_flow"

  private val partitioning1 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyX", "keyY", "keyZ"],
      |   "keysToValuesMap": {
      |     "keyX": "value1",
      |     "keyZ": "value3",
      |     "keyY": "value2"
      |   }
      |}
      |""".stripMargin
  )

  private val partitioning2 = JsonBString(
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

  test("Get partitioning main flow returns main flow for partitioning with such data") {
    table("runs.partitionings").insert(
      add("partitioning", partitioning1)
        .add("created_by", "Sirius")
    )

    table("runs.partitionings").insert(
      add("partitioning", partitioning2)
        .add("created_by", "Snape")
    )

    val fkPartitioning1: Long = table("runs.partitionings").fieldValue("partitioning", partitioning1, "id_partitioning").get.get
    val fkPartitioning2: Long = table("runs.partitionings").fieldValue("partitioning", partitioning2, "id_partitioning").get.get

    table("flows.partitioning_to_flow").insert(
      add("fk_flow", 1L)
        .add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Sirius")
    )

    table("flows.partitioning_to_flow").insert(
      add("fk_flow", 2L)
        .add("fk_partitioning", fkPartitioning2)
        .add("created_by", "Snape")
    )

    table("flows.flows").insert(
      add("id_flow", 1L)
        .add("flow_name", "Flow1")
        .add("flow_description", "test flow 1")
        .add("from_pattern", false)
        .add("created_by", "Sirius")
        .add("fk_primary_partitioning", fkPartitioning1)
    )

    table("flows.flows").insert(
      add("id_flow", 2L)
        .add("flow_name", "Flow2")
        .add("flow_description", "test flow 2")
        .add("from_pattern", true)
        .add("created_by", "Snape")
        .add("fk_primary_partitioning", fkPartitioning2)
    )

    function(fncGetPartitioningMainFlow)
      .setParam("i_partitioning_id", fkPartitioning1)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getLong("id_flow").contains(1L))
        assert(results.getString("flow_name").contains("Flow1"))
        assert(results.getString("flow_description").contains("test flow 1"))
        assert(results.getBoolean("from_pattern").contains(false))

        assert(!queryResult.hasNext)
      }

    function(fncGetPartitioningMainFlow)
      .setParam("i_partitioning_id", fkPartitioning2)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getLong("id_flow").contains(2L))
        assert(results.getString("flow_name").contains("Flow2"))
        assert(results.getString("flow_description").contains("test flow 2"))
        assert(results.getBoolean("from_pattern").contains(true))

        assert(!queryResult.hasNext)
      }

    table("flows.flows").where(add("fk_primary_partitioning", fkPartitioning1)) { queryResult =>
      assert(queryResult.hasNext)

      val row = queryResult.next()
      assert(row.getLong("id_flow").contains(1L))
      assert(row.getString("flow_name").contains("Flow1"))
      assert(row.getString("created_by").contains("Sirius"))

      assert(!queryResult.hasNext)
    }

    table("flows.flows").where(add("fk_primary_partitioning", fkPartitioning2)) { queryResult =>
      assert(queryResult.hasNext)

      val row = queryResult.next()
      assert(row.getLong("id_flow").contains(2L))
      assert(row.getString("flow_name").contains("Flow2"))
      assert(row.getString("created_by").contains("Snape"))

      assert(!queryResult.hasNext)
    }

  }

  test("Get partitioning main flow should return error for partitioning with no main flow (not typical/valid scenario)") {
    table("runs.partitionings").insert(
      add("partitioning", partitioning2)
        .add("created_by", "Dobby")
    )

    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning2, "id_partitioning").get.get

    function(fncGetPartitioningMainFlow)
      .setParam("i_partitioning_id", fkPartitioning)
      .execute { queryResult =>
        val result = queryResult.next()
        assert(result.getInt("status").contains(50))
        assert(result.getString("status_text").contains("Main flow not found"))
        assert(!queryResult.hasNext)
      }

    table("runs.additional_data").where(add("fk_partitioning", fkPartitioning)) { additionalDataResult =>
      assert(!additionalDataResult.hasNext)
    }
  }

  test("Get partitioning main flow should return error status code on non existing partitioning") {
    function(fncGetPartitioningMainFlow)
      .setParam("i_partitioning_id", 0L)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(41))
        assert(results.getString("status_text").contains("Partitioning not found"))
        assert(!queryResult.hasNext)
      }
  }

}

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

import za.co.absa.atum.tags.IntegrationTestTag
import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString
import za.co.absa.balta.classes.setter.CustomDBType

class GetPartitioningMeasuresTest extends DBTestSuite {
  private val fncGetPartitioningMeasures = "runs.get_partitioning_measures"

  test("Get partitioning measures should return partitioning measures for partitioning with measures", IntegrationTestTag) {
    val partitioning = JsonBString(
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

    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", "Thomas")
    )

    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.measure_definitions").insert(
      add("fk_partitioning", fkPartitioning)
        .add("created_by", "Thomas")
        .add("measure_name", "measure1")
        .add("measured_columns", CustomDBType("""{"col1"}""", "TEXT[]"))
    )

    table("runs.measure_definitions").insert(
      add("fk_partitioning", fkPartitioning)
        .add("created_by", "Thomas")
        .add("measure_name", "measure2")
        .add("measured_columns", CustomDBType("""{"col2"}""", "TEXT[]"))
    )

    function(fncGetPartitioningMeasures)
      .setParam("i_partitioning", partitioning)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("OK"))
        assert(results.getString("measure_name").contains("measure1"))
        assert(results.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col1")))

        val results2 = queryResult.next()
        assert(results2.getInt("status").contains(11))
        assert(results2.getString("status_text").contains("OK"))
        assert(results2.getString("measure_name").contains("measure2"))
        assert(results2.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col2")))

        assert(!queryResult.hasNext)
      }

    table("runs.measure_definitions").where(add("fk_partitioning", fkPartitioning)) { partitioningMeasuresResult =>
      assert(partitioningMeasuresResult.hasNext)
      val row = partitioningMeasuresResult.next()
      assert(row.getString("created_by").contains("Thomas"))
    }
  }

  test("Get partitioning measures should return error status code on non existing partitioning", IntegrationTestTag) {
    val partitioning = JsonBString(
      """
        |{
        |   "version": 1,
        |   "keys": ["key1"],
        |   "keysToValues": {
        |     "key1": "value1"
        |   }
        |}
        |""".stripMargin
    )

    function(fncGetPartitioningMeasures)
      .setParam("i_partitioning", partitioning)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(41))
        assert(results.getString("status_text").contains("Partitioning not found"))
        assert(!queryResult.hasNext)
      }
  }

  test("Get partitioning measures should return no data status code on partitioning without measures", IntegrationTestTag) {
    val partitioning = JsonBString(
      """
        |{
        |  "keys": [
        |    "keyA",
        |    "keyB",
        |    "keyC"
        |  ],
        |  "version": 1,
        |  "keysToValues": {
        |    "keyA": "valueA",
        |    "keyB": "valueB",
        |    "keyC": "valueC"
        |  }
        |}
        |""".stripMargin
    )

    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", "Thomas")
    )

    function(fncGetPartitioningMeasures)
      .setParam("i_partitioning", partitioning)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }
  }

}


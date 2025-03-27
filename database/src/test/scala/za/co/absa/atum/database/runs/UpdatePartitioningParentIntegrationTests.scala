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
import za.co.absa.balta.classes.setter.CustomDBType

class UpdatePartitioningParentIntegrationTests extends DBTestSuite {

  private val updateParentFn = "runs.update_partitioning_parent"
  private val createPartitioningFn = "runs.create_partitioning"
  private val fncGetPartitioningAdditionalData = "runs.get_partitioning_additional_data"
  private val fncGetPartitioningMeasuresById = "runs.get_partitioning_measures_by_id"

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

  //Data Preparation Step
  def dataPreparation(): (Long, Long, Long) = {
    val nonExistentID = 9999L

    val parentPartitioningIDTest = function(createPartitioningFn)
      .setParam("i_partitioning", parentPartitioning)
      .setParam("i_by_user", "Albert Einstein")
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    val childPartitioningIDTest = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Albert Einstein")
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    table("runs.additional_data").insert(
      add("fk_partitioning", parentPartitioningIDTest)
        .add("created_by", "Joseph")
        .add("ad_name", "ad_1")
        .add("ad_value", "This is the additional data for Joseph")
    )

    table("runs.additional_data").insert(
      add("fk_partitioning", parentPartitioningIDTest)
        .add("created_by", "Joseph")
        .add("ad_name", "ad_2")
        .add("ad_value", "This is the additional data for Joseph")
    )

    table("runs.measure_definitions").insert(
      add("fk_partitioning", parentPartitioningIDTest)
        .add("created_by", "Joseph")
        .add("measure_name", "measure1")
        .add("measured_columns", CustomDBType("""{"col1"}""", "TEXT[]"))
    )

    table("runs.measure_definitions").insert(
      add("fk_partitioning", parentPartitioningIDTest)
        .add("created_by", "Joseph")
        .add("measure_name", "measure2")
        .add("measured_columns", CustomDBType("""{"col2"}""", "TEXT[]"))
    )

    (nonExistentID, parentPartitioningIDTest, childPartitioningIDTest)
  }

  //Check for child partitioning
  test("Child Partitioning not found") {

    val (nonExistentID, parentPartitioningIDTest, childPartitioningIDTest) = dataPreparation()

    function(updateParentFn)
      .setParam("i_id_partitioning", nonExistentID)
      .setParam("i_id_parent_partitioning", parentPartitioningIDTest)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_copy_measurements", true)
      .setParam("i_copy_additional_data", true)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Child Partitioning not found"))
        assert(!queryResult.hasNext)
      }
  }

  //Check for Parent Partitioning
  test("Parent Partitioning not found") {

    val (nonExistentID, parentPartitioningIDTest, childPartitioningIDTest) = dataPreparation()

    function(updateParentFn)
      .setParam("i_id_partitioning", childPartitioningIDTest)
      .setParam("i_id_parent_partitioning", nonExistentID)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_copy_measurements", true)
      .setParam("i_copy_additional_data", true)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(42))
        assert(row.getString("status_text").contains("Parent Partitioning not found"))
        assert(!queryResult.hasNext)
      }
  }

  //Update Parent with no additional data and no measurements
  test("Parent Partitioning Updated no additional data and no measurements") {

    val (nonExistentID, parentPartitioningIDTest, childPartitioningIDTest) = dataPreparation()

    function(updateParentFn)
      .setParam("i_id_partitioning", childPartitioningIDTest)
      .setParam("i_id_parent_partitioning", parentPartitioningIDTest)
      .setParam("i_by_user", "Happy Nappy")
      .setParam("i_copy_measurements", false)
      .setParam("i_copy_additional_data", false)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(!queryResult.hasNext)
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningIDTest)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", childPartitioningIDTest)) == 2
    )

    function(fncGetPartitioningAdditionalData)
      .setParam("i_partitioning_id", childPartitioningIDTest)
      .execute { queryResult =>
        val result = queryResult.next()
        assert(result.getInt("status").contains(16))
        assert(result.getString("status_text").contains("No additional data found"))
        assert(!queryResult.hasNext)
      }

    function(fncGetPartitioningMeasuresById)
      .setParam(childPartitioningIDTest)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }
  }

  //Update Parent with additional data and no measurements
  test("Parent Partitioning Updated with additional data and no measurements") {

    val (nonExistentID, parentPartitioningIDTest, childPartitioningIDTest) = dataPreparation()

    function(updateParentFn)
      .setParam("i_id_partitioning", childPartitioningIDTest)
      .setParam("i_id_parent_partitioning", parentPartitioningIDTest)
      .setParam("i_by_user", "Happy Nappy")
      .setParam("i_copy_measurements", false)
      .setParam("i_copy_additional_data", true)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(!queryResult.hasNext)
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningIDTest)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", childPartitioningIDTest)) == 2
    )

    function(fncGetPartitioningAdditionalData)
      .setParam("i_partitioning_id", childPartitioningIDTest)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("OK"))
        assert(results.getString("ad_name").contains("ad_1"))
        assert(results.getString("ad_value").contains("This is the additional data for Joseph"))
        assert(results.getString("ad_author").contains("Happy Nappy"))

        val results2 = queryResult.next()
        assert(results2.getInt("status").contains(11))
        assert(results2.getString("status_text").contains("OK"))
        assert(results2.getString("ad_name").contains("ad_2"))
        assert(results2.getString("ad_value").contains("This is the additional data for Joseph"))
        assert(results2.getString("ad_author").contains("Happy Nappy"))
        assert(!queryResult.hasNext)
      }

    function(fncGetPartitioningMeasuresById)
      .setParam(childPartitioningIDTest)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }
  }

  //Update Parent with additional data and with measurements
  test("Parent Partitioning Updated with additional data and with measurements") {

    val (nonExistentID, parentPartitioningIDTest, childPartitioningIDTest) = dataPreparation()

    function(updateParentFn)
      .setParam("i_id_partitioning", childPartitioningIDTest)
      .setParam("i_id_parent_partitioning", parentPartitioningIDTest)
      .setParam("i_by_user", "Happy Nappy")
      .setParam("i_copy_measurements", true)
      .setParam("i_copy_additional_data", true)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(!queryResult.hasNext)
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningIDTest)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", childPartitioningIDTest)) == 2
    )

    function(fncGetPartitioningAdditionalData)
      .setParam("i_partitioning_id", childPartitioningIDTest)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("OK"))
        assert(results.getString("ad_name").contains("ad_1"))
        assert(results.getString("ad_value").contains("This is the additional data for Joseph"))
        assert(results.getString("ad_author").contains("Happy Nappy"))

        val results2 = queryResult.next()
        assert(results2.getInt("status").contains(11))
        assert(results2.getString("status_text").contains("OK"))
        assert(results2.getString("ad_name").contains("ad_2"))
        assert(results2.getString("ad_value").contains("This is the additional data for Joseph"))
        assert(results2.getString("ad_author").contains("Happy Nappy"))
        assert(!queryResult.hasNext)
      }

    function(fncGetPartitioningMeasuresById)
      .setParam("i_partitioning_id", childPartitioningIDTest)
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
  }
}

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

class GetPartitioningAdditionalDataTest extends DBTestSuite{

  private val fncGetPartitioningAdditionalData = "runs.get_partitioning_additional_data"

  private val partitioning1 = JsonBString(
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

  test("Get partitioning additional data returns additional data for partitioning with additional data") {
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

    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Joseph")
        .add("ad_name", "ad_1")
        .add("ad_value", "This is the additional data for Joseph")
        .add("updated_by", "Joseph")
    )

    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Joseph")
        .add("ad_name", "ad_2")
        .add("ad_value", "This is the additional data for Joseph")
        .add("updated_by", "Joseph")
    )

    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning2)
        .add("created_by", "Daniel")
        .add("ad_name", "ad_3")
        .add("ad_value", "This is the additional data for Daniel")
        .add("updated_by", "Daniel")
    )

    function(fncGetPartitioningAdditionalData)
      .setParam("i_partitioning", partitioning1)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("OK"))
        assert(results.getString("ad_name").contains("ad_1"))
        assert(results.getString("ad_value").contains("This is the additional data for Joseph"))

        val results2 = queryResult.next()
        assert(results2.getInt("status").contains(11))
        assert(results2.getString("status_text").contains("OK"))
        assert(results2.getString("ad_name").contains("ad_2"))
        assert(results2.getString("ad_value").contains("This is the additional data for Joseph"))

        assert(!queryResult.hasNext)
      }

    table("runs.additional_data").where(add("fk_partitioning", fkPartitioning1)) { additionalDataResult =>
      assert(additionalDataResult.hasNext)
      val row = additionalDataResult.next()
      assert(row.getString("ad_name").contains("ad_1"))
      assert(row.getString("ad_value").contains("This is the additional data for Joseph"))
      assert(row.getString("created_by").contains("Joseph"))
    }

  }

  test("Get partitioning additional data should return no records for partitioning without additional data") {
    table("runs.partitionings").insert(
      add("partitioning", partitioning2)
        .add("created_by", "Joseph")
    )

    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning2, "id_partitioning").get.get

    function(fncGetPartitioningAdditionalData)
      .setParam("i_partitioning", partitioning2)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(16))
        assert(results.getString("status_text").contains("No additional data found for the given partitioning."))
        assert(results.getString("ad_name").isEmpty)
        assert(!queryResult.hasNext)
      }

    table("runs.additional_data").where(add("fk_partitioning", fkPartitioning)) { additionalDataResult =>
      assert(!additionalDataResult.hasNext)
    }
  }

  test("Get partitioning additional data should return error status code on non existing partitioning") {
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

    function(fncGetPartitioningAdditionalData)
      .setParam("i_partitioning", partitioning)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(41))
        assert(results.getString("status_text").contains("The partitioning does not exist."))
        assert(!queryResult.hasNext)
      }
  }

}

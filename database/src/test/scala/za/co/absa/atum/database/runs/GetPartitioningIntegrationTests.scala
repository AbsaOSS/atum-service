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

import io.circe.Json
import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString

class GetPartitioningIntegrationTests extends DBTestSuite {

  private val fnCreatePartitioning = "runs.create_partitioning"
  private val fnGetPartitioning = "runs.get_partitioning"

  private val partitioning1Value =
    """
      |{
      |  "version":1,
      |  "keys":["key1","key2","key3","key4"],
      |  "keysToValuesMap":{
      |    "key1":"valueX",
      |    "key2":"valueY",
      |    "key3":"valueZ",
      |    "key4":"valueA"
      |  }
      |}
      |""".stripMargin

  private val partitioning1 = JsonBString(partitioning1Value)

  private val partitioning2 = JsonBString(
    """
      |{
      |  "version":1,
      |  "keys":["key1","key2","key3","key4"],
      |  "keysToValuesMap":{
      |    "key1":"valueX",
      |    "key2":"valueX",
      |    "key3":"valueX",
      |    "key4":"valueX"
      |  }
      |}
      |""".stripMargin
  )

  test("Existing (correct) partitioning is returned") {
    val partitioning1ID = function(fnCreatePartitioning)
      .setParam("i_partitioning", partitioning1)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning_id")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    function(fnCreatePartitioning)
      .setParam("i_partitioning", partitioning2)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning_id")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
      }

    function(fnGetPartitioning)
      .setParam("i_partitioning", partitioning1)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("id").contains(partitioning1ID))
        assert {
          val retrievedPartitioningAsJson = Json.fromString(row.getJsonB("o_partitioning").get.value)
          val expectedPartitioningAsJson = Json.fromString(partitioning1Value)
          retrievedPartitioningAsJson \\ "keysToValuesMap" == expectedPartitioningAsJson \\ "keysToValuesMap" &&
            retrievedPartitioningAsJson \\ "keys" == expectedPartitioningAsJson \\ "keys"
        }
        assert(row.getString("author").contains("Fantômas"))
        assert(!queryResult.hasNext)
      }
  }

  test("Non-existent partitioning is not returned") {
    function(fnGetPartitioning)
      .setParam("i_partitioning", partitioning1)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Partitioning not found"))
        assert(row.getLong("id").isEmpty)
        assert(row.getJsonB("o_partitioning").isEmpty)
        assert(row.getString("author").isEmpty)
        assert(!queryResult.hasNext)
      }
  }

}

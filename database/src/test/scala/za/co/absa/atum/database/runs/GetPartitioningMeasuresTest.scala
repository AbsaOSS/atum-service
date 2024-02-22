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


class GetPartitioningMeasuresTest extends DBTestSuite{

  private val fncGetPartitioningMeasures = "runs.get_partitioning_measures"

  test("Get partitioning measures should return partitioning measures for partitioning with measures") {
    val partitioning = JsonBString(
      """
        |{
        |  "keys": [
        |    "key1",
        |    "key2"
        |  ],
        |  "version": 1,
        |  "keysToValues": {
        |    "key1": "value1",
        |    "key2": "value2"
        |  }
        |}
        |""".stripMargin
    )

    function(fncGetPartitioningMeasures)
      .setParam("i_partitioning", partitioning)
      .execute { queryResult =>
        val results = queryResult.distinct.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("OK"))
      }
  }

  test("Get partitioning measures should return error on non existing partitioning") {
    val partitioning = JsonBString(
      """
        |{
        |   "version": 1,
        |   "keys": ["key1"],
        |   "values": {
        |     "key1": "value1"
        |   }
        |}
        |""".stripMargin
    )

    function(fncGetPartitioningMeasures)
      .setParam("i_partitioning", partitioning)
      .execute { queryResult =>
        val results = queryResult.distinct.next()
        assert(results.getInt("status").contains(41))
        assert(results.getString("status_text").contains("The partitioning does not exist."))
      }
  }

  test("Get partitioning measures should return exception for partitioning with no measures") {
    val partitioning = JsonBString(
      """
        |{
        |  "keys": [
        |    "key1",
        |    "key3",
        |    "key5"
        |  ],
        |  "version": 1,
        |  "keysToValues": {
        |    "key1": "value1",
        |    "key3": "value3",
        |    "key5": "value5"
        |  }
        |}
        |""".stripMargin
    )

    function(fncGetPartitioningMeasures)
      .setParam("i_partitioning", partitioning)
      .execute { queryResult =>
        val results = queryResult.distinct.next()
        assert(results.getInt("status").contains(42))
        assert(results.getString("status_text").contains("No partitioning measures match the provided partitioning."))
      }
  }

}

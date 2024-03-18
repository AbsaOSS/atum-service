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

import org.postgresql.util.PSQLException
import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString

class IsPartitioningValidTest extends DBTestSuite {

  private val fncIsPartitioningValid = "validation.is_partitioning_valid"

  private val partitioningValid = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2", "key3", "key4"],
      |  "keysToValues": {
      |    "key1": "valueA",
      |    "key2": "valueB",
      |    "key3": "valueC",
      |    "key4": "valueD"
      |  }
      |}
      |""".stripMargin
  )

  private val partitioningInvalid = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2missing", "key3", "key4"],
      |  "keysToValues": {
      |    "key1": "valueX",
      |    "key3": "valueZ",
      |    "key4": "valueA"
      |  }
      |}
      |""".stripMargin
  )


  private val partitioningValidWithNullValues = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2", "key3", "key4"],
      |  "keysToValues": {
      |    "key1": "valueX",
      |    "key2": "valueY",
      |    "key3": null,
      |    "key4": null
      |  }
      |}
      |""".stripMargin
  )

  test("Partitioning is valid (strict check)") {
   function(fncIsPartitioningValid)
      .setParam("i_partitioning", partitioningValid)
      .execute { queryResult =>
        assert(queryResult.hasNext)

        val row = queryResult.next()
        val isValid = row.resultSet.getBoolean(1)

        assert(isValid)
      }
  }

  test("Partitioning is valid but has null values (strict check)") {
    val caught = intercept[PSQLException](
      function(fncIsPartitioningValid)
        .setParam("i_partitioning", partitioningValidWithNullValues)
        .execute { queryResult => queryResult }
      )

    assert(
      caught.getMessage.startsWith(s"ERROR: The input partitioning is not valid:")
    )
  }

  test("Partitioning is valid but has null values (non-strict check)") {
    function(fncIsPartitioningValid)
      .setParam("i_partitioning", partitioningValidWithNullValues)
      .setParam("i_strict_check", false)
      .execute { queryResult =>
        assert(queryResult.hasNext)

        val row = queryResult.next()
        val isValid = row.resultSet.getBoolean(1)

        assert(isValid)
      }
  }

  test("Partitioning is invalid") {
    val caught = intercept[PSQLException](
      function(fncIsPartitioningValid)
        .setParam("i_partitioning", partitioningInvalid)
        .execute { queryResult => queryResult }
    )

    assert(
      caught.getMessage.startsWith(s"ERROR: The input partitioning is not valid:")
    )
  }
}

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

class ValidatePartitioningTest extends DBTestSuite {

  private val fncValidatePartitioning = "validation.validate_partitioning"

  private val partitioningValid = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2", "key3"],
      |  "keysToValues": {
      |    "key1": "valueA",
      |    "key2": "valueB",
      |    "key3": "valueC"
      |  }
      |}
      |""".stripMargin
  )

  private val partitioningValidWhitespacesInKeys = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2 ", " key2", " "],
      |  "keysToValues": {
      |    "key1": "valueA",
      |    "key2 ": "valueB",
      |    " key2": "valueC",
      |    " ": "valueD"
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

  private val partitioningValidMissingValue = JsonBString(
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


  private val partitioningValidValuesNotCorrespondToKeys = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2", "key3"],
      |  "keysToValues": {
      |    "key1": "valueA",
      |    "key2": "valueB",
      |    "key4": "valueC"
      |  }
      |}
      |""".stripMargin
  )

  private val partitioningInvalidIncompatibleVersion = JsonBString(
    """
      |{
      |  "version": 999,
      |  "keys": ["key1", "key2", "key3"],
      |  "keysToValues": {
      |    "key1": "valueA",
      |    "key2": "valueB",
      |    "key3": "valueC"
      |  }
      |}
      |""".stripMargin
  )

  private val partitioningValidRedundantAttribute = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2", "key3"],
      |  "keysToValues": {
      |    "key1": "valueA",
      |    "key2": "valueB",
      |    "key3": "valueC"
      |  },
      |  "redundantAttribute": 1
      |}
      |""".stripMargin
  )
  private val partitioningInvalidBadJSONStructure1 = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2", "key3"],
      |  "badKeysToValues": {
      |    "key1": "valueA",
      |    "key2": "valueB",
      |    "key3": "valueC"
      |  }
      |}
      |""".stripMargin
  )

  private val partitioningInvalidDuplicatedKeys = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2", "key2"],
      |  "keysToValues": {
      |    "key1": "valueA",
      |    "key2": "valueB"
      |  }
      |}
      |""".stripMargin
  )

  private val partitioningInvalidMultipleProblems = JsonBString(
    """
      |{
      |  "version": 999,
      |  "keys": ["key1", "key2", "key2"],
      |  "keysToValues": {
      |    "key1": "valueA",
      |    "key2": "valueB"
      |  }
      |}
      |""".stripMargin
  )

  test("Partitioning is valid (strict check)") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningValid)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is valid, even when keys have whitespaces (without them they would be duplicated)") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningValidWhitespacesInKeys)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is valid but has null values (strict check)") {
      function(fncValidatePartitioning)
        .setParam("i_partitioning", partitioningValidWithNullValues)
        .execute { queryResult =>
          assert(queryResult.hasNext)

          val row = queryResult.next()
          val errorDetails = row.resultSet.getString(1)

          assert(errorDetails.startsWith("The input partitioning is invalid, some values in 'keysToValues' are NULLs:"))
          assert(!queryResult.hasNext)
        }
  }

  test("Partitioning is valid, but one key doesn't have a value (strict check)") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningValidMissingValue)
      .execute { queryResult =>
        assert(queryResult.hasNext)

        val row = queryResult.next()
        val errorDetails = row.resultSet.getString(1)

        assert(
          errorDetails.startsWith(
            "The input partitioning is invalid, the keys in 'keys' and 'keysToValues' do not correspond."
          )
        )
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is valid, but one key doesn't have a value (non-strict check)") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningValidMissingValue)
      .setParam("i_strict_check", false)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is valid, but keys don't correspond to values (non-strict check)") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningValidValuesNotCorrespondToKeys)
      .setParam("i_strict_check", false)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is valid, but keys don't correspond to values (strict check)") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningValidValuesNotCorrespondToKeys)
      .execute { queryResult =>
        assert(queryResult.hasNext)

        val row = queryResult.next()
        val errorDetails = row.resultSet.getString(1)

        assert(
          errorDetails.startsWith(
            "The input partitioning is invalid, the keys in 'keys' and 'keysToValues' do not correspond."
          )
        )
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is invalid, incompatible version") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningInvalidIncompatibleVersion)
      .execute { queryResult =>
        assert(queryResult.hasNext)

        val row = queryResult.next()
        val errorDetails = row.resultSet.getString(1)

        assert(errorDetails.startsWith("The input partitioning is not of the supported version."))
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is valid, but it has redundant attribute") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningValidRedundantAttribute)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is invalid, valid JSON but incorrect structure (attribute missing)") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningInvalidBadJSONStructure1)
      .execute { queryResult =>
        assert(queryResult.hasNext)

        val row = queryResult.next()
        val errorDetails = row.resultSet.getString(1)

        assert(errorDetails.startsWith("The input partitioning is not properly structured"))
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is invalid, duplicated partition keys") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningInvalidDuplicatedKeys)
      .execute { queryResult =>
        assert(queryResult.hasNext)

        val row = queryResult.next()
        val errorDetails = row.resultSet.getString(1)

        assert(
          errorDetails.startsWith("The input partitioning is invalid, the keys must be unique and can not contain NULLs")
        )
        assert(!queryResult.hasNext)
      }
  }

  test("Partitioning is invalid, multiple problems") {
    function(fncValidatePartitioning)
      .setParam("i_partitioning", partitioningInvalidMultipleProblems)
      .execute { queryResult =>
        assert(queryResult.hasNext)

        val row1 = queryResult.next()
        val errorDetails1 = row1.resultSet.getString(1)

        assert(queryResult.hasNext)
        val row2 = queryResult.next()
        val errorDetails2 = row2.resultSet.getString(1)

        assert(
          errorDetails1.startsWith("The input partitioning is not of the supported version") &&
          errorDetails2.startsWith("The input partitioning is invalid, the keys must be unique and can not contain NULLs")
        )

        assert(!queryResult.hasNext)
      }
  }
}

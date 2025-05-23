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

import io.circe.parser.parse
import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString

import scala.collection.mutable
import scala.util.control.Breaks.{break, breakable}

class GetPartitioningAncestorsIntegrationTests extends DBTestSuite {

  private val getAncestorsFn = "runs.get_partitioning_ancestors"
  private val createPartitioningFn = "runs.create_partitioning_if_not_exists"

  private val addToParentFlowsFn = "flows._add_to_parent_flows"

  private val partitioning1 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyA", "keyB", "keyC"],
      |   "keysToValuesMap": {
      |     "keyA": "valueA",
      |     "keyB": "valueB",
      |     "keyC": "valueC"
      |   }
      |}
      |""".stripMargin
  )

  private val expectedPartitioning1 = parse(partitioning1.value).getOrElse(throw new Exception("Failed to parse JSON"))

  private val partitioning2 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyD", "keyE", "keyF"],
      |   "keysToValuesMap": {
      |     "keyD": "valueD",
      |     "keyE": "valueE",
      |     "keyF": "valueF"
      |   }
      |}
      |""".stripMargin
  )

  private val expectedPartitioning2 = parse(partitioning2.value).getOrElse(throw new Exception("Failed to parse JSON"))

  private val partitioning3 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyG", "keyH", "keyI"],
      |   "keysToValuesMap": {
      |     "keyG": "valueG",
      |     "keyH": "valueH",
      |     "keyI": "valueI"
      |   }
      |}
      |""".stripMargin
  )

  private val expectedPartitioning3 = parse(partitioning3.value).getOrElse(throw new Exception("Failed to parse JSON"))

  private val partitioning4 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyJ", "keyK", "keyL"],
      |   "keysToValuesMap": {
      |     "keyJ": "valueJ",
      |     "keyK": "valueK",
      |     "keyL": "valueL"
      |   }
      |}
      |""".stripMargin
  )

  private val expectedPartitioning4 = parse(partitioning4.value).getOrElse(throw new Exception("Failed to parse JSON"))

  private val partitioning5 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyM", "keyN", "keyO"],
      |   "keysToValuesMap": {
      |     "keyM": "valueM",
      |     "keyN": "valueN",
      |     "keyO": "valueO"
      |   }
      |}
      |""".stripMargin
  )

  private val expectedPartitioning5 = parse(partitioning5.value).getOrElse(throw new Exception("Failed to parse JSON"))

  private val partitioning6 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyP", "keyQ", "keyR"],
      |   "keysToValuesMap": {
      |     "keyP": "valueP",
      |     "keyQ": "valueQ",
      |     "keyR": "valueR"
      |   }
      |}
      |""".stripMargin
  )

  private val expectedPartitioning6 = parse(partitioning6.value).getOrElse(throw new Exception("Failed to parse JSON"))

  private val partitioning7 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyS", "keyT", "keyU"],
      |   "keysToValuesMap": {
      |     "keyS": "valueS",
      |     "keyT": "valueT",
      |     "keyU": "valueU"
      |   }
      |}
      |""".stripMargin
  )

  private val expectedPartitioning7 = parse(partitioning7.value).getOrElse(throw new Exception("Failed to parse JSON"))

  private val partitioning8 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyV", "keyW", "keyX"],
      |   "keysToValuesMap": {
      |     "keyV": "valueV",
      |     "keyW": "valueW",
      |     "keyX": "valueX"
      |   }
      |}
      |""".stripMargin
  )

  // Testing for return of the Ancestors for a given Partition ID
  //
  //  1(Grandma)  2(Grandpa)
  //      |           |
  //  3(Mother)   4(Father)    6(Daughter)
  //     \        |                |
  //       5(Son)           7(Granddaughter)
  //          |            /
  //           8(Grandson)
  test("Returns Ancestors for a given Partition ID"){

    //Data Preparation Step Start ------------------------------------------------------------------------------------
    val partitioningID1 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning1)
      .setParam("i_by_user", "Grandma")
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    val partitioningID2 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning2)
      .setParam("i_by_user", "Grandpa")
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    val partitioningID3 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning3)
      .setParam("i_by_user", "Mother")
      .setParam("i_parent_partitioning", partitioning1)
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    val partitioningID4 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning4)
      .setParam("i_by_user", "Father")
      .setParam("i_parent_partitioning", partitioning2)
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    val partitioningID5 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning5)
      .setParam("i_by_user", "Son")
      .setParam("i_parent_partitioning", partitioning3)
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partitioningID4)
      .setParam("i_fk_partitioning", partitioningID5)
      .setParam("i_by_user", "Son")
      .execute { queryResult =>
        queryResult.next()
      }

    val partitioningID6 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning6)
      .setParam("i_by_user", "Daughter")
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    val partitioningID7 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning7)
      .setParam("i_by_user", "Granddaughter")
      .setParam("i_parent_partitioning", partitioning6)
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    val partitioningID8 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning8)
      .setParam("i_by_user", "Grandson")
      .setParam("i_parent_partitioning", partitioning5)
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partitioningID7)
      .setParam("i_fk_partitioning", partitioningID8)
      .setParam("i_by_user", "Grandson")
      .execute { queryResult =>
        queryResult.next()
      }

    //Used Linked Hash Map to keep structure and order
    val resultsMapFor5 = mutable.LinkedHashMap(
      "Grandma" -> (partitioningID1, expectedPartitioning1),
      "Grandpa" -> (partitioningID2, expectedPartitioning2),
      "Mother" -> (partitioningID3, expectedPartitioning3),
      "Father" -> (partitioningID4, expectedPartitioning4),
    )

    //Used Linked Hash Map to keep structure and order
    val resultsMapFor8 = mutable.LinkedHashMap(
      "Grandma" -> (partitioningID1, expectedPartitioning1),
      "Grandpa" -> (partitioningID2, expectedPartitioning2),
      "Mother" -> (partitioningID3, expectedPartitioning3),
      "Father" -> (partitioningID4, expectedPartitioning4),
      "Son" -> (partitioningID5, expectedPartitioning5),
      "Daughter" -> (partitioningID6, expectedPartitioning6),
      "Granddaughter" -> (partitioningID7, expectedPartitioning7)
    )
    //Data Preparation Step End --------------------------------------------------------------------------------------

    //Test 1 Ancestor
    function(getAncestorsFn)
      .setParam("i_id_partitioning", partitioningID3)
      .execute { queryResult =>
        val row = queryResult.next()
        val returnedPartitioning = row.getJsonB("partitioning").get
        val returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))

        assert(row.getInt("status").contains(10))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestor_id").contains(partitioningID1))
        assert(returnedPartitioningParsed == expectedPartitioning1)
        assert(row.getString("author").contains("Grandma"))
        assert(!queryResult.hasNext)
      }

    //Test Multiple Ancestors
    function(getAncestorsFn)
      .setParam("i_id_partitioning", partitioningID5)
      .execute { queryResult =>
        for ((k, v) <- resultsMapFor5) {
          assert(queryResult.hasNext)

          val row = queryResult.next()
          val returnedPartitioning = row.getJsonB("partitioning").get
          val returnedPartitioningParsed = parse(returnedPartitioning.value)
            .getOrElse(fail("Failed to parse returned partitioning"))
          val expectedPartitioningId = v._1
          val expectedPartitioning = v._2
          val expectedAuthor = k

          assert(row.getInt("status").contains(10))
          assert(row.getString("status_text").contains("OK"))
          assert(row.getLong("ancestor_id").contains(expectedPartitioningId))
          assert(returnedPartitioningParsed == expectedPartitioning)
          assert(row.getString("author").contains(expectedAuthor))
        }
        assert(!queryResult.hasNext)
      }

    //TEST Separate flow for Ancestors Partitions
    function(getAncestorsFn)
      .setParam("i_id_partitioning", partitioningID7)
      .execute { queryResult =>
        val row = queryResult.next()
        val returnedPartitioning = row.getJsonB("partitioning").get
        val returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))

        assert(row.getInt("status").contains(10))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestor_id").contains(partitioningID6))
        assert(returnedPartitioningParsed == expectedPartitioning6)
        assert(row.getString("author").contains("Daughter"))
        assert(!queryResult.hasNext)
      }

    //TEST ALL flows for Ancestors Partitions
    function(getAncestorsFn)
      .setParam("i_id_partitioning", partitioningID8)
      .execute { queryResult =>
        for ((k, v) <- resultsMapFor8) {
          assert(queryResult.hasNext)

          val row = queryResult.next()
          val returnedPartitioning = row.getJsonB("partitioning").get
          val returnedPartitioningParsed = parse(returnedPartitioning.value)
            .getOrElse(fail("Failed to parse returned partitioning"))
          val expectedPartitioningId = v._1
          val expectedPartitioning = v._2
          val expectedAuthor = k

          assert(row.getInt("status").contains(10))
          assert(row.getString("status_text").contains("OK"))
          assert(row.getLong("ancestor_id").contains(expectedPartitioningId))
          assert(returnedPartitioningParsed == expectedPartitioning)
          assert(row.getString("author").contains(expectedAuthor))
        }
        assert(!queryResult.hasNext)
      }
  }

  //First Failure Test: Child Partition not found
  test("Child Partitioning not found") {
    val nonExistentID = 9999L

    function(getAncestorsFn)
      .setParam("i_id_partitioning", nonExistentID)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Partitioning not found"))
        assert(!queryResult.hasNext)
      }
  }

  //Second Failure Test: Ancestor Partitioning not found
  test("Ancestor Partitioning not found") {

    val partitioningID1 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning1)
      .setParam("i_by_user", "Grandma")
      .execute { queryResult =>
        val row = queryResult.next()
        row.getLong("id_partitioning").get
      }

    function(getAncestorsFn)
      .setParam("i_id_partitioning", partitioningID1)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(14))
        assert(row.getString("status_text").contains("OK"))
        assert(!queryResult.hasNext)
      }
  }
}

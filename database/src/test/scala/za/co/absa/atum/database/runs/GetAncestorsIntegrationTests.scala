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

import java.time.OffsetDateTime

class GetAncestorsIntegrationTests extends DBTestSuite {

  private val getAncestorsFn = "runs.get_ancestors"
  private val partitioningsTable = "runs.partitionings"
  private val createPartitioningFn = "runs.create_partitioning"

  private val createFlowFn = "flows._create_flow"
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

  var flowIdOfPartitioning1: Long = _
  var flowIdOfPartitioning2: Long = _
  var flowIdOfPartitioning3: Long = _

  test("Returns Ancestors for a given Partition ID") {

    val Time1 = OffsetDateTime.parse("1992-08-03T10:00:00Z")
    val Time2 = OffsetDateTime.parse("1992-08-04T10:00:00Z")
    val Time3 = OffsetDateTime.parse("1992-08-05T10:00:00Z")
    val Time4 = OffsetDateTime.parse("1992-08-06T10:00:00Z")
    val Time5 = OffsetDateTime.parse("1992-08-07T10:00:00Z")
    val Time6 = OffsetDateTime.parse("1992-08-08T10:00:00Z")
    val Time7 = OffsetDateTime.parse("1992-08-09T10:00:00Z")
    val Time8 = OffsetDateTime.parse("1992-08-09T11:00:00Z")

    table(partitioningsTable).insert(add("partitioning", partitioning1).add("created_by", "Grandpa").add("created_at", Time1))
    table(partitioningsTable).insert(add("partitioning", partitioning2).add("created_by", "Father").add("created_at", Time2))
    table(partitioningsTable).insert(add("partitioning", partitioning3).add("created_by", "Son").add("created_at", Time3))
    table(partitioningsTable).insert(add("partitioning", partitioning4).add("created_by", "Grandson").add("created_at", Time4))
    table(partitioningsTable).insert(add("partitioning", partitioning5).add("created_by", "Grandma").add("created_at", Time5))
    table(partitioningsTable).insert(add("partitioning", partitioning6).add("created_by", "Mother").add("created_at", Time6))
    table(partitioningsTable).insert(add("partitioning", partitioning7).add("created_by", "Daughter").add("created_at", Time7))
    table(partitioningsTable).insert(add("partitioning", partitioning8).add("created_by", "Granddaughter").add("created_at", Time8))

    val partId1: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning1, "id_partitioning").get.get

    val partId2: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning2, "id_partitioning").get.get

    val partId3: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning3, "id_partitioning").get.get

    val partId4: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning4, "id_partitioning").get.get

    val partId5: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning5, "id_partitioning").get.get

    val partId6: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning6, "id_partitioning").get.get

    val partId7: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning7, "id_partitioning").get.get

    val partId8: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning8, "id_partitioning").get.get

    function(createFlowFn)
      .setParam("i_fk_partitioning", partId1)
      .setParam("i_by_user", "Grandpa")
      .execute { queryResult =>
        flowIdOfPartitioning1 = queryResult.next().getLong("id_flow").get
      }

    function(createFlowFn)
      .setParam("i_fk_partitioning", partId2)
      .setParam("i_by_user", "Father")
      .execute { queryResult =>
        flowIdOfPartitioning2 = queryResult.next().getLong("id_flow").get
      }

    function(createFlowFn)
      .setParam("i_fk_partitioning", partId6)
      .setParam("i_by_user", "Daughter")
      .execute { queryResult =>
        flowIdOfPartitioning3 = queryResult.next().getLong("id_flow").get
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partId1)
      .setParam("i_fk_partitioning", partId3)
      .setParam("i_by_user", "Son")
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "Partitioning added to flows")
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partId2)
      .setParam("i_fk_partitioning", partId4)
      .setParam("i_by_user", "Grandson")
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "Partitioning added to flows")
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partId6)
      .setParam("i_fk_partitioning", partId7)
      .setParam("i_by_user", "GrandDaughter")
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "Partitioning added to flows")
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partId3)
      .setParam("i_fk_partitioning", partId5)
      .setParam("i_by_user", "GrandMa")
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "Partitioning added to flows")
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partId4)
      .setParam("i_fk_partitioning", partId5)
      .setParam("i_by_user", "GrandMa")
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "Partitioning added to flows")
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partId5)
      .setParam("i_fk_partitioning", partId8)
      .setParam("i_by_user", "Mother")
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "Partitioning added to flows")
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partId7)
      .setParam("i_fk_partitioning", partId8)
      .setParam("i_by_user", "Mother")
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "Partitioning added to flows")
      }

    //TEST 1 Ancestors Partition
    function(getAncestorsFn)
      .setParam("i_id_partitioning", partId3)
      .execute { queryResult =>
        val row = queryResult.next()
        val returnedPartitioning = row.getJsonB("partitioning").get
        val returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId1))
        assert(returnedPartitioningParsed == expectedPartitioning1)
        assert(row.getString("author").contains("Grandpa"))
        assert(row.getString("author").contains("Grandpa"))
      }

    //TEST multiple Ancestors Partitions
    function(getAncestorsFn)
      .setParam("i_id_partitioning", partId5)
      .execute { queryResult =>
        var row = queryResult.next()
        var returnedPartitioning = row.getJsonB("partitioning").get
        var returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId1))
        assert(returnedPartitioningParsed == expectedPartitioning1)
        assert(row.getString("author").contains("Grandpa"))
        row = queryResult.next()
        returnedPartitioning = row.getJsonB("partitioning").get
        returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId2))
        assert(returnedPartitioningParsed == expectedPartitioning2)
        assert(row.getString("author").contains("Father"))
        row = queryResult.next()
        returnedPartitioning = row.getJsonB("partitioning").get
        returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId3))
        assert(returnedPartitioningParsed == expectedPartitioning3)
        assert(row.getString("author").contains("Son"))
        row = queryResult.next()
        returnedPartitioning = row.getJsonB("partitioning").get
        returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId4))
        assert(returnedPartitioningParsed == expectedPartitioning4)
        assert(row.getString("author").contains("Grandson"))
      }

    //TEST Separate flow for Ancestors Partitions
    function(getAncestorsFn)
      .setParam("i_id_partitioning", partId7)
      .execute { queryResult =>
        val row = queryResult.next()
        val returnedPartitioning = row.getJsonB("partitioning").get
        val returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId6))
        assert(returnedPartitioningParsed == expectedPartitioning6)
        assert(row.getString("author").contains("Mother"))
      }

    //TEST ALL flows for Ancestors Partitions
    function(getAncestorsFn)
      .setParam("i_id_partitioning", partId8)
      .setParam("i_limit", 10)
      .execute { queryResult =>
        var row = queryResult.next()
        var returnedPartitioning = row.getJsonB("partitioning").get
        var returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId1))
        assert(returnedPartitioningParsed == expectedPartitioning1)
        assert(row.getString("author").contains("Grandpa"))
        row = queryResult.next()
        returnedPartitioning = row.getJsonB("partitioning").get
        returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId2))
        assert(returnedPartitioningParsed == expectedPartitioning2)
        assert(row.getString("author").contains("Father"))
        row = queryResult.next()
        returnedPartitioning = row.getJsonB("partitioning").get
        returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId3))
        assert(returnedPartitioningParsed == expectedPartitioning3)
        assert(row.getString("author").contains("Son"))
        row = queryResult.next()
        returnedPartitioning = row.getJsonB("partitioning").get
        returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId4))
        assert(returnedPartitioningParsed == expectedPartitioning4)
        assert(row.getString("author").contains("Grandson"))
        row = queryResult.next()
        returnedPartitioning = row.getJsonB("partitioning").get
        returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId5))
        assert(returnedPartitioningParsed == expectedPartitioning5)
        assert(row.getString("author").contains("Grandma"))
        row = queryResult.next()
        returnedPartitioning = row.getJsonB("partitioning").get
        returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId6))
        assert(returnedPartitioningParsed == expectedPartitioning6)
        assert(row.getString("author").contains("Mother"))
        row = queryResult.next()
        returnedPartitioning = row.getJsonB("partitioning").get
        returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId7))
        assert(returnedPartitioningParsed == expectedPartitioning7)
        assert(row.getString("author").contains("Daughter"))
        }
  }

  test("Change in Parent") {

    val Time1 = OffsetDateTime.parse("1992-08-03T10:00:00Z")

    table("runs.partitionings").insert(
        add("partitioning", partitioning1)
        .add("created_by", "GrandPa")
        .add("created_at", Time1)
    )

    val partId1: Long = table("runs.partitionings").fieldValue("partitioning", partitioning1, "id_partitioning").get.get

    function(createFlowFn)
      .setParam("i_fk_partitioning", partId1)
      .setParam("i_by_user", "GrandPa")
      .execute { queryResult =>
        flowIdOfPartitioning1 = queryResult.next().getLong("id_flow").get
      }

    val partId3 = function(createPartitioningFn)
      .setParam("i_partitioning", partitioning3)
      .setParam("i_parent_partitioning_id", partId1)
      .setParam("i_by_user", "Father")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(12))
        assert(row.getString("status_text").contains("Partitioning created with parent partitioning"))
        row.getLong("id_partitioning").get
      }

    function(getAncestorsFn)
      .setParam("i_id_partitioning", partId3)
      .execute { queryResult =>
        val row = queryResult.next()
        val returnedPartitioning = row.getJsonB("partitioning").get
        val returnedPartitioningParsed = parse(returnedPartitioning.value)
          .getOrElse(fail("Failed to parse returned partitioning"))
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("ancestorid").contains(partId1))
        assert(returnedPartitioningParsed == expectedPartitioning1)
        assert(row.getString("author").contains("GrandPa"))
      }
  }

  test("Child Partitioning not found") {
    val nonExistentID = 9999L

    function(getAncestorsFn)
      .setParam("i_id_partitioning", nonExistentID)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Partitioning not found"))
        assert(row.getJsonB("ancestorid").isEmpty)
        assert(row.getJsonB("partitioning").isEmpty)
        assert(row.getString("author").isEmpty)
        assert(!queryResult.hasNext)
      }
  }

  test("Ancestor Partitioning not found") {

    table(partitioningsTable).insert(add("partitioning", partitioning5).add("created_by", "NO_Ancestor"))

    val partId5: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning5, "id_partitioning").get.get

    function(createFlowFn)
      .setParam("i_fk_partitioning", partId5)
      .setParam("i_by_user", "Grandpa")
      .execute { queryResult =>
        flowIdOfPartitioning1 = queryResult.next().getLong("id_flow").get
      }

    function(getAncestorsFn)
      .setParam("i_id_partitioning", partId5)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(42))
        assert(row.getString("status_text").contains("Ancestor Partitioning not found"))
        assert(row.getJsonB("ancestorid").isEmpty)
        assert(row.getJsonB("partitioning").isEmpty)
        assert(row.getString("author").isEmpty)
        assert(!queryResult.hasNext)
      }
  }
}

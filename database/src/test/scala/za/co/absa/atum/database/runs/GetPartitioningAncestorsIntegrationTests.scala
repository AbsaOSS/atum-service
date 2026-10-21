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
import za.co.absa.balta.classes.setter.CustomDBType

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

  // ---- Multi-parent lineage (LINEAGE-01) ------------------------------------------------------------------------

  private val createMultiFn = "runs.create_partitioning_with_parents"
  private val createPlainFn = "runs.create_partitioning"

  private def lineagePartitioning(name: String): JsonBString = JsonBString(
    s"""
       |{
       |  "version": 1,
       |  "keys": ["lineage"],
       |  "keysToValuesMap": {
       |    "lineage": "$name"
       |  }
       |}
       |""".stripMargin
  )

  private def ids(xs: Seq[Any]): CustomDBType =
    CustomDBType(xs.map(x => if (x == null) "NULL" else x.toString).mkString("{", ",", "}"), "BIGINT[]")

  private def createParent(p: JsonBString, author: String): Long = {
    function(createPlainFn)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", author)
      .setParamNull("i_parent_partitioning_id")
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        assert(row.getInt("status").contains(11))
        row.getLong("id_partitioning").get
      }
  }

  // returns (status, id_partitioning)
  private def createMulti(p: JsonBString, parents: CustomDBType): (Int, Long) = {
    function(createMultiFn)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", "Child")
      .setParam("i_parent_partitioning_ids", parents)
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        (row.getInt("status").get, row.getLong("id_partitioning").get)
      }
  }

  // returns (ancestorId, status, hasMore) per returned row
  private def rows(id: Long, limit: Int = 10, offset: Long = 0L): Seq[(Option[Long], Int, Boolean)] = {
    function(getAncestorsFn)
      .setParam("i_id_partitioning", id)
      .setParam("i_limit", limit)
      .setParam("i_offset", offset)
      .execute { qr =>
        Iterator
          .continually(qr)
          .takeWhile(_.hasNext)
          .map { q =>
            val row = q.next()
            (row.getLong("ancestor_id"), row.getInt("status").get, row.getBoolean("has_more").getOrElse(false))
          }
          .toList
      }
  }

  private def ancestorIds(id: Long): Seq[Long] = rows(id).flatMap(_._1)

  test("Returns both parents for a child with two parents") {
    val p1 = createParent(lineagePartitioning("two-p1"), "Parent1")
    val p2 = createParent(lineagePartitioning("two-p2"), "Parent2")
    val (status, child) = createMulti(lineagePartitioning("two-child"), ids(Seq(p1, p2)))
    assert(status == 12)

    function(getAncestorsFn)
      .setParam("i_id_partitioning", child)
      .execute { qr =>
        val expected = Seq((p1, "two-p1", "Parent1"), (p2, "two-p2", "Parent2")).sortBy(_._1)
        expected.foreach { case (id, name, author) =>
          assert(qr.hasNext)
          val row = qr.next()
          assert(row.getInt("status").contains(10))
          assert(row.getString("status_text").contains("OK"))
          assert(row.getLong("ancestor_id").contains(id))
          assert(row.getString("author").contains(author))
          assert(row.getBoolean("has_more").contains(false))
          val json = parse(row.getJsonB("partitioning").get.value).getOrElse(fail("Failed to parse partitioning"))
          assert(json == parse(lineagePartitioning(name).value).getOrElse(fail("Failed to parse expected")))
        }
        assert(!qr.hasNext)
      }
  }

  test("Three parents: each ancestor exactly once, resubmits do not duplicate") {
    val p1 = createParent(lineagePartitioning("three-p1"), "Parent1")
    val p2 = createParent(lineagePartitioning("three-p2"), "Parent2")
    val p3 = createParent(lineagePartitioning("three-p3"), "Parent3")
    val childP = lineagePartitioning("three-child")
    val (status, child) = createMulti(childP, ids(Seq(p1, p2, p3)))
    assert(status == 12)

    val first = ancestorIds(child)
    assert(first.size == 3)
    assert(first.distinct.size == 3)
    assert(first == Seq(p1, p2, p3).sorted)
    assert(rows(child).forall(_._2 == 10))

    // duplicate id in array, all parents already linked -> nothing changed
    val (status2, child2) = createMulti(childP, ids(Seq(p1, p2, p1)))
    assert(status2 == 14)
    assert(child2 == child)
    assert(ancestorIds(child) == Seq(p1, p2, p3).sorted)

    // add a new parent -> data added
    val p4 = createParent(lineagePartitioning("three-p4"), "Parent4")
    val (status3, _) = createMulti(childP, ids(Seq(p1, p2, p3, p4)))
    assert(status3 == 13)
    val last = ancestorIds(child)
    assert(last.size == 4)
    assert(last.distinct.size == 4)
    assert(last == Seq(p1, p2, p3, p4).sorted)
  }

  test("Diamond: each ancestor exactly once") {
    val gp = createParent(lineagePartitioning("diamond-gp"), "GP")
    val (s1, p1) = createMulti(lineagePartitioning("diamond-p1"), ids(Seq(gp)))
    val (s2, p2) = createMulti(lineagePartitioning("diamond-p2"), ids(Seq(gp)))
    assert(s1 == 12 && s2 == 12)
    val (s3, child) = createMulti(lineagePartitioning("diamond-child"), ids(Seq(p1, p2)))
    assert(s3 == 12)

    val result = rows(child)
    assert(result.size == 3)
    assert(result.forall(_._2 == 10))
    val resultIds = result.flatMap(_._1)
    assert(resultIds.distinct.size == 3)
    assert(resultIds == Seq(gp, p1, p2).sorted)
  }

  test("Pagination over multiple parents is consistent with has_more") {
    val parents = (1 to 3).map(i => createParent(lineagePartitioning(s"page-p$i"), s"Parent$i")).sorted
    val (status, child) = createMulti(lineagePartitioning("page-child"), ids(parents))
    assert(status == 12)

    val page1 = rows(child, 2, 0)
    assert(page1.flatMap(_._1) == parents.take(2))
    assert(page1.forall(r => r._2 == 10 && r._3))

    val page2 = rows(child, 2, 2)
    assert(page2.flatMap(_._1) == parents.drop(2))
    assert(page2.forall(r => r._2 == 10 && !r._3))

    val all = rows(child, 3, 0)
    assert(all.flatMap(_._1) == parents)
    assert(all.forall(!_._3))

    val single = rows(child, 1, 1)
    assert(single.flatMap(_._1) == Seq(parents(1)))
    assert(single.forall(_._3))

    val union = page1.flatMap(_._1) ++ page2.flatMap(_._1)
    assert(union == parents)
    assert(union.distinct.size == union.size)
  }

  test("Single parent via create_partitioning_with_parents and no-ancestor regression") {
    val p1 = createParent(lineagePartitioning("single-p1"), "Parent1")
    val (status, child) = createMulti(lineagePartitioning("single-child"), ids(Seq(p1)))
    assert(status == 12)
    assert(ancestorIds(child) == Seq(p1))

    val noAncestors = rows(p1)
    assert(noAncestors.size == 1)
    assert(noAncestors.head == ((None, 14, false)))
  }

  test("Unique index unq_flows prevents a second flow for the same primary partitioning") {
    val p1 = createParent(lineagePartitioning("unq-p1"), "Parent1")
    assert(table("flows.flows").count("fk_primary_partitioning = " + p1) == 1)

    val ex = intercept[java.sql.SQLException] {
      table("flows.flows").insert(
        add("flow_name", "duplicate flow")
          .add("from_pattern", false)
          .add("created_by", "tester")
          .add("fk_primary_partitioning", p1)
      )
    }
    assert(ex.getSQLState == "23505")
  }
}

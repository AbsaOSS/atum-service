/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import io.circe.parser.parse
import org.postgresql.util.PSQLException
import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.{DBFunction, JsonBString}

import java.time.OffsetDateTime
import java.util.UUID

class SearchPartitioningsIntegrationTests extends DBTestSuite {

  private val searchFn = "runs.search_partitionings"
  private val author = "Search author"
  private val fromTime = OffsetDateTime.parse("2026-01-10T00:00:00Z")
  private val toTime = OffsetDateTime.parse("2026-01-20T00:00:00Z")
  private val pagingKeys = Some(JsonBString("""{"group":"paging"}"""))

  private def insertPartitioning(keys: (String, String)*): (Long, Json) = {
    val partitioning = Json.obj(
      "version" -> Json.fromInt(1),
      "keys" -> Json.arr(keys.map { case (key, _) => Json.fromString(key) }: _*),
      "keysToValuesMap" -> Json.obj(keys.map { case (key, value) => key -> Json.fromString(value) }: _*)
    )
    val json = JsonBString(partitioning.noSpaces)
    table("runs.partitionings").insert(add("partitioning", json).add("created_by", author))
    val id: Long = table("runs.partitionings").fieldValue("partitioning", json, "id_partitioning").get.get
    (id, partitioning)
  }

  private def insertCheckpoint(partitioningId: Long, startTime: OffsetDateTime): Unit = {
    table("runs.checkpoints").insert(
      add("id_checkpoint", UUID.randomUUID())
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "Search checkpoint")
        .add("process_start_time", startTime)
        .add("created_by", author)
    )
  }

  private def query(
    keys: Option[JsonBString] = None,
    from: Option[OffsetDateTime] = None,
    to: Option[OffsetDateTime] = None,
    limit: Option[Int] = Some(10),
    offset: Option[Long] = Some(0L)
  ): DBFunction = {
    val base = function(searchFn)
    val withLimit = limit.fold(base.setParamNull("i_limit"): DBFunction)(base.setParam("i_limit", _))
    val withOffset = offset.fold(withLimit.setParamNull("i_offset"): DBFunction)(withLimit.setParam("i_offset", _))
    val withKeys = keys.fold(withOffset.setParamNull("i_keys"): DBFunction)(withOffset.setParam("i_keys", _))
    val withFrom = from.fold(withKeys.setParamNull("i_from_time"): DBFunction)(withKeys.setParam("i_from_time", _))
    to.fold(withFrom.setParamNull("i_to_time"): DBFunction)(withFrom.setParam("i_to_time", _))
  }

  private def assertPage(search: DBFunction, expected: Seq[(Long, Json)], hasMore: Boolean = false): Unit = {
    search.execute { rows =>
      expected.sortBy(_._1).foreach { case (id, partitioning) =>
        assert(rows.hasNext)
        val row = rows.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("id").contains(id))
        assert(parse(row.getJsonB("partitioning").get.value) == Right(partitioning))
        assert(row.getString("author").contains(author))
        assert(row.getBoolean("has_more").contains(hasMore))
      }
      assert(!rows.hasNext)
    }
  }

  test("Key-only searches require every key and preserve subset envelopes and stored key order without checkpoints") {
    val first = insertPartitioning("env" -> "prod", "country" -> "za", "extra" -> "first")
    val second = insertPartitioning("extra" -> "second", "country" -> "za", "env" -> "prod")
    insertPartitioning("country" -> "za")
    insertPartitioning("env" -> "test", "country" -> "za")
    insertPartitioning("env" -> "prod", "country" -> "uk")

    assertPage(query(keys = Some(JsonBString("""{"country":"za","env":"prod"}"""))), Seq(first, second))
    assertPage(query(keys = Some(JsonBString("""{"country":"za","env":"prod","missing":"value"}"""))), Seq.empty)
  }

  test("Keys and values are exact strings including Unicode, special characters and empty strings") {
    val specialKey = "\u00e9\"\\/?%_"
    val specialValue = "\u4e2d\u20ac\"\\\n' OR TRUE -- &+="
    val matching = insertPartitioning("" -> "", specialKey -> specialValue, "extra" -> "kept")
    insertPartitioning("" -> "not empty", specialKey -> specialValue)
    insertPartitioning("" -> "", specialKey -> s"$specialValue suffix")
    val filter = JsonBString(Json.obj("" -> Json.fromString(""), specialKey -> Json.fromString(specialValue)).noSpaces)

    assertPage(query(keys = Some(filter)), Seq(matching))
  }

  Seq(
    ("inclusive from and exclusive to", Some(fromTime), Some(toTime), Seq(1, 2)),
    ("from only", Some(fromTime), None, Seq(1, 2, 3, 4)),
    ("to only", None, Some(toTime), Seq(0, 1, 2))
  ).foreach { case (description, from, to, expectedIndices) =>
    test(s"Time-only searches use any checkpoint with $description and allow empty keys") {
      val partitionings = (0 until 6).map(index => insertPartitioning("time-row" -> index.toString))
      Seq(fromTime.minusNanos(1000), fromTime, fromTime.plusDays(1), toTime, toTime.plusDays(1))
        .zip(partitionings).foreach { case (time, (id, _)) => insertCheckpoint(id, time) }
      insertCheckpoint(partitionings(1)._1, toTime.plusDays(10))
      insertCheckpoint(partitionings(2)._1, fromTime.plusDays(2))
      val expected = expectedIndices.map(partitionings)

      assertPage(query(from = from, to = to), expected)
      assertPage(query(keys = Some(JsonBString("{}")), from = from, to = to), expected)
    }
  }

  test("Combined keys and time filters intersect rather than union their matches") {
    val historicalMatch = insertPartitioning("env" -> "prod", "row" -> "historical")
    val insideMatch = insertPartitioning("env" -> "prod", "row" -> "inside")
    val wrongKeys = insertPartitioning("env" -> "test", "row" -> "inside")
    val wrongTime = insertPartitioning("env" -> "prod", "row" -> "outside")
    insertPartitioning("env" -> "prod", "row" -> "no checkpoints")
    insertCheckpoint(historicalMatch._1, fromTime)
    insertCheckpoint(historicalMatch._1, toTime.plusDays(1))
    insertCheckpoint(insideMatch._1, fromTime.plusDays(1))
    insertCheckpoint(wrongKeys._1, fromTime)
    insertCheckpoint(wrongTime._1, toTime)

    assertPage(
      query(keys = Some(JsonBString("""{"env":"prod"}""")), from = Some(fromTime), to = Some(toTime)),
      Seq(historicalMatch, insideMatch)
    )
  }

  test("Multiple flow memberships and checkpoints do not duplicate a partitioning") {
    val matching = insertPartitioning("group" -> "multiple-flows")
    val roots = Seq(insertPartitioning("root" -> "one"), insertPartitioning("root" -> "two"))
    roots.foreach { case (id, _) =>
      function("flows._create_flow")
        .setParam("i_fk_partitioning", id)
        .setParam("i_by_user", author)
        .execute(rows => assert(rows.next().getInt("status").contains(11)))
      function("flows._add_to_parent_flows")
        .setParam("i_fk_parent_partitioning", id)
        .setParam("i_fk_partitioning", matching._1)
        .setParam("i_by_user", author)
        .execute(rows => assert(rows.next().getInt("status").contains(11)))
    }
    (0 until 3).foreach(day => insertCheckpoint(matching._1, fromTime.plusDays(day)))

    assertPage(
      query(keys = Some(JsonBString("""{"group":"multiple-flows"}""")), from = Some(fromTime), limit = Some(1)),
      Seq(matching)
    )
  }

  Seq(
    ("first page", 2, 0L, Seq(0, 1), true),
    ("middle page", 2, 2L, Seq(2, 3), true),
    ("last partial page", 2, 4L, Seq(4), false),
    ("exact final full page", 2, 3L, Seq(3, 4), false),
    ("offset at the end", 2, 5L, Seq.empty[Int], false),
    ("offset beyond the end", 2, 6L, Seq.empty[Int], false),
    ("minimum limit", 1, 0L, Seq(0), true),
    ("maximum limit", 1000, 0L, Seq(0, 1, 2, 3, 4), false)
  ).foreach { case (description, limit, offset, expectedIndices, hasMore) =>
    test(s"Distinct partitionings are paginated by ID with correct has_more for $description") {
      val partitionings = (0 until 5).map(index => insertPartitioning("group" -> "paging", "row" -> index.toString))
      partitionings.reverse.foreach { case (id, _) =>
        (0 until 3).foreach(day => insertCheckpoint(id, fromTime.plusDays(day)))
      }
      val expected = expectedIndices.map(partitionings.sortBy(_._1))
      assertPage(
        query(keys = pagingKeys, from = Some(fromTime), to = Some(toTime), limit = Some(limit), offset = Some(offset)),
        expected,
        hasMore
      )
      assertPage(query(keys = pagingKeys, limit = Some(limit), offset = Some(offset)), expected, hasMore)
      Seq(None, Some(JsonBString("{}"))).foreach { keys =>
        assertPage(
          query(keys = keys, from = Some(fromTime), to = Some(toTime), limit = Some(limit), offset = Some(offset)),
          expected,
          hasMore
        )
      }
    }
  }

  test("Time-only pagination excludes orphan checkpoints before applying offset and limit") {
    val partitionings = (0 until 3).map(index => insertPartitioning("time-page" -> index.toString))
    partitionings.foreach { case (id, _) =>
      insertCheckpoint(id, fromTime)
      insertCheckpoint(id, fromTime.plusDays(1))
    }
    insertCheckpoint(Long.MinValue, fromTime)
    insertCheckpoint(Long.MaxValue, fromTime)

    Seq(None, Some(JsonBString("{}"))).foreach { keys =>
      assertPage(query(keys = keys, from = Some(fromTime), limit = Some(2)), partitionings.take(2), hasMore = true)
      assertPage(
        query(keys = keys, from = Some(fromTime), limit = Some(2), offset = Some(2L)), partitionings.drop(2)
      )
      assertPage(query(keys = keys, from = Some(fromTime), limit = Some(2), offset = Some(3L)), Seq.empty)
    }
  }

  test("A time-only search reaches a partitioning after one thousand lower-ID matches") {
    val partitionings = (0 to 1000).map { index =>
      val partitioning = insertPartitioning("time-page" -> index.toString)
      insertCheckpoint(partitioning._1, fromTime)
      partitioning
    }.sortBy(_._1)
    insertCheckpoint(partitionings.head._1, fromTime)

    assertPage(
      query(from = Some(fromTime), to = Some(fromTime.plusNanos(1000000)), limit = Some(1000)),
      partitionings.take(1000),
      hasMore = true
    )
    assertPage(
      query(from = Some(fromTime), to = Some(fromTime.plusNanos(1000000)), limit = Some(1000), offset = Some(1000L)),
      partitionings.drop(1000)
    )
  }

  test("Default limit and offset return the first ten partitionings") {
    val partitionings = (0 until 12).map(index => insertPartitioning("group" -> "defaults", "row" -> index.toString))
    assertPage(
      function(searchFn).setParam("i_keys", JsonBString("""{"group":"defaults"}""")),
      partitionings.sortBy(_._1).take(10),
      hasMore = true
    )
  }

  test("No matching keys or checkpoints return zero rows") {
    val partitioning = insertPartitioning("env" -> "prod")
    insertCheckpoint(partitioning._1, fromTime)
    assertPage(query(keys = Some(JsonBString("""{"env":"absent"}"""))), Seq.empty)
    assertPage(query(from = Some(toTime)), Seq.empty)
  }

  Seq[(String, () => DBFunction, String)](
    ("NULL limit", () => query(keys = pagingKeys, limit = None), "i_limit"),
    ("zero limit", () => query(keys = pagingKeys, limit = Some(0)), "i_limit"),
    ("negative limit", () => query(keys = pagingKeys, limit = Some(-1)), "i_limit"),
    ("oversized limit", () => query(keys = pagingKeys, limit = Some(1001)), "i_limit"),
    ("NULL offset", () => query(keys = pagingKeys, offset = None), "i_offset"),
    ("negative offset", () => query(keys = pagingKeys, offset = Some(-1L)), "i_offset"),
    ("equal time bounds", () => query(from = Some(fromTime), to = Some(fromTime)), "i_from_time"),
    ("reversed time bounds", () => query(from = Some(toTime), to = Some(fromTime)), "i_from_time"),
    ("no filters", () => function(searchFn), "At least one"),
    ("empty keys without time", () => query(keys = Some(JsonBString("{}"))), "At least one")
  ).foreach { case (description, invalidQuery, message) =>
    test(s"Rejects $description with invalid_parameter_value") {
      val error = intercept[PSQLException](invalidQuery().execute(_ => ()))
      assert(error.getSQLState == "22023")
      assert(error.getMessage.startsWith(s"ERROR: $message"))
    }
  }

  Seq(
    "[]", "\"text\"", "42", "true", "null",
    """{"key":42}""", """{"key":true}""", """{"key":null}""",
    """{"key":[]}""", """{"key":{}}""", """{"valid":"value","invalid":42}"""
  ).foreach { invalidKeys =>
    test(s"Rejects non-string-map keys $invalidKeys even with a time filter") {
      val error = intercept[PSQLException](
        query(keys = Some(JsonBString(invalidKeys)), from = Some(fromTime)).execute(_ => ())
      )
      assert(error.getSQLState == "22023")
      assert(error.getMessage.startsWith("ERROR: i_keys must be a JSON object of string values"))
    }
  }
}

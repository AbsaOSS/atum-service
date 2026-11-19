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
import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.{DBFunction, JsonBString}

import java.time.OffsetDateTime
import java.util.UUID

class SearchPartitioningsIntegrationTests extends DBTestSuite {

  private val searchFn = "runs.search_partitionings"
  private val searchSignature =
    "runs.search_partitionings(integer,bigint,jsonb,timestamp with time zone,timestamp with time zone,boolean,boolean,text,text)"
  private val helperSignature = "runs._partitioning_matches_search_text(jsonb,text)"
  private val author = "Search author"
  private val fromTime = OffsetDateTime.parse("2026-01-10T00:00:00Z")
  private val toTime = OffsetDateTime.parse("2026-01-20T00:00:00Z")
  private val pagingKeys = Some(JsonBString("""{"group":"paging"}"""))

  private def insertPartitioning(keys: (String, String)*): (Long, Json) =
    insertPartitioningByAuthor(author, keys: _*)

  private def insertPartitioningByAuthor(createdBy: String, keys: (String, String)*): (Long, Json) = {
    val partitioning = Json.obj(
      "version" -> Json.fromInt(1),
      "keys" -> Json.arr(keys.map { case (key, _) => Json.fromString(key) }: _*),
      "keysToValuesMap" -> Json.obj(keys.map { case (key, value) => key -> Json.fromString(value) }: _*)
    )
    val json = JsonBString(partitioning.noSpaces)
    table("runs.partitionings").insert(add("partitioning", json).add("created_by", createdBy))
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
    offset: Option[Long] = Some(0L),
    latestFirst: Option[Boolean] = None,
    exactKeys: Option[Boolean] = None,
    author: Option[String] = None,
    searchText: Option[String] = None
  ): DBFunction = {
    val base = function(searchFn)
    val withLimit = limit.fold(base.setParamNull("i_limit"): DBFunction)(base.setParam("i_limit", _))
    val withOffset = offset.fold(withLimit.setParamNull("i_offset"): DBFunction)(withLimit.setParam("i_offset", _))
    val withKeys = keys.fold(withOffset.setParamNull("i_keys"): DBFunction)(withOffset.setParam("i_keys", _))
    val withFrom = from.fold(withKeys.setParamNull("i_from_time"): DBFunction)(withKeys.setParam("i_from_time", _))
    val withTo = to.fold(withFrom.setParamNull("i_to_time"): DBFunction)(withFrom.setParam("i_to_time", _))
    val withLatest = latestFirst.fold(withTo)(withTo.setParam("i_latest_first", _))
    val withExact = exactKeys.fold(withLatest)(withLatest.setParam("i_exact_keys", _))
    val withAuthor = author.fold(withExact)(withExact.setParam("i_author", _))
    searchText.fold(withAuthor)(withAuthor.setParam("i_search_text", _))
  }

  private def assertPage(
    search: DBFunction,
    expected: Seq[(Long, Json)],
    hasMore: Boolean = false,
    descending: Boolean = false,
    expectedAuthor: String = author
  ): Unit = {
    val ordered = if (descending) expected.sortBy(-_._1) else expected.sortBy(_._1)
    search.execute { rows =>
      ordered.foreach { case (id, partitioning) =>
        assert(rows.hasNext)
        val row = rows.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(row.getLong("id").contains(id))
        assert(parse(row.getJsonB("partitioning").get.value) == Right(partitioning))
        assert(row.getString("author").contains(expectedAuthor))
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

  test("Key-existence (null value), mixed key/value + key-existence, and i_exact_keys match in any key order") {
    val twoKeysForward = insertPartitioning("table_name" -> "abc", "country" -> "za")
    val twoKeysReverse = insertPartitioning("country" -> "za", "table_name" -> "abc")
    val twoKeysOtherVal = insertPartitioning("country" -> "uk", "table_name" -> "xyz")
    val threeKeysMatch = insertPartitioning("table_name" -> "abc", "info_date" -> "2025-12-12", "country" -> "za")
    val threeKeysOtherCountry =
      insertPartitioning("country" -> "uk", "table_name" -> "abc", "info_date" -> "2025-12-12")
    insertPartitioning("table_name" -> "abc", "info_date" -> "2025-12-12")
    insertPartitioning("country" -> "za")

    insertCheckpoint(twoKeysForward._1, fromTime)
    insertCheckpoint(twoKeysOtherVal._1, fromTime)
    insertCheckpoint(threeKeysMatch._1, toTime)

    val keyNamesOnly = Some(JsonBString("""{"table_name":null,"country":null}"""))
    val exactKv = Some(JsonBString("""{"table_name":"abc","country":"za"}"""))
    val mixed = Some(JsonBString("""{"table_name":"abc","country":null}"""))

    // Partial key-names only (any values, any extra keys, any key order)
    assertPage(
      query(keys = keyNamesOnly),
      Seq(twoKeysForward, twoKeysReverse, twoKeysOtherVal, threeKeysMatch, threeKeysOtherCountry)
    )
    // Exact key-names only (only {table_name, country}, any values, any key order)
    assertPage(
      query(keys = keyNamesOnly, exactKeys = Some(true)),
      Seq(twoKeysForward, twoKeysReverse, twoKeysOtherVal)
    )
    // Exact key-names only + checkpoint time window
    assertPage(
      query(keys = keyNamesOnly, from = Some(fromTime), to = Some(toTime), exactKeys = Some(true)),
      Seq(twoKeysForward, twoKeysOtherVal)
    )
    // Partial key-value vs exact key-value
    assertPage(
      query(keys = exactKv, exactKeys = Some(false)),
      Seq(twoKeysForward, twoKeysReverse, threeKeysMatch)
    )
    assertPage(
      query(keys = exactKv, exactKeys = Some(true)),
      Seq(twoKeysForward, twoKeysReverse)
    )
    // Mixed key-value + key-existence (partial and exact, with and without time window)
    assertPage(
      query(keys = mixed),
      Seq(twoKeysForward, twoKeysReverse, threeKeysMatch, threeKeysOtherCountry)
    )
    assertPage(
      query(keys = mixed, exactKeys = Some(true)),
      Seq(twoKeysForward, twoKeysReverse)
    )
    assertPage(
      query(keys = mixed, from = Some(fromTime), to = Some(toTime), exactKeys = Some(true)),
      Seq(twoKeysForward)
    )
  }

  test("i_author and i_search_text work standalone and combined with keys, exact-keys, and time bounds") {
    val aqueduct1 = insertPartitioningByAuthor(
      "aqueduct",
      "catalog_path" -> "edla_prod_raw.aq_core_payments",
      "info_date" -> "2026-11-11"
    )
    val aqueduct2 = insertPartitioningByAuthor(
      "aqueduct",
      "catalog_path" -> "edla_prod_raw.aq_crm_customers",
      "info_date" -> "2026-11-11"
    )
    val unify1 = insertPartitioningByAuthor(
      "unify",
      "catalog_path" -> "edla_prod_curated.un_customer_payments",
      "info_date" -> "2026-11-11"
    )
    val unifyWildcards = insertPartitioningByAuthor(
      "unify",
      "special_key_100%" -> "path\\with_literal%chars"
    )

    insertCheckpoint(aqueduct1._1, fromTime)
    insertCheckpoint(unify1._1, fromTime)

    // Author alone (includes partitionings without checkpoints)
    assertPage(
      query(author = Some("aqueduct")),
      Seq(aqueduct1, aqueduct2),
      expectedAuthor = "aqueduct"
    )
    // Author + checkpoint time window
    assertPage(
      query(author = Some("aqueduct"), from = Some(fromTime), to = Some(toTime)),
      Seq(aqueduct1),
      expectedAuthor = "aqueduct"
    )
    // Case-insensitive search_text matching value substring ("CUSTOMER_PAYMENTS" contains literal underscore)
    assertPage(
      query(searchText = Some("CUSTOMER_PAYMENTS")),
      Seq(unify1),
      expectedAuthor = "unify"
    )
    // Case-insensitive search_text matching key name ("SPECIAL_KEY_100%") and literal %, _, \ escaping
    assertPage(
      query(searchText = Some("SPECIAL_KEY_100%")),
      Seq(unifyWildcards),
      expectedAuthor = "unify"
    )
    assertPage(
      query(searchText = Some("with_literal%chars")),
      Seq(unifyWildcards),
      expectedAuthor = "unify"
    )
    assertPage(
      query(searchText = Some("path\\with")),
      Seq(unifyWildcards),
      expectedAuthor = "unify"
    )
    // Underscore and percent in i_search_text do not act as SQL LIKE wildcards
    assertPage(
      query(author = Some("unify"), searchText = Some("unXcustomer")),
      Seq.empty
    )
    assertPage(
      query(author = Some("unify"), searchText = Some("un%customer")),
      Seq.empty
    )
    // JSON envelope punctuation (":") does not cause false-positive matches on jsonb_each_text
    assertPage(
      query(author = Some("unify"), searchText = Some("\":\"")),
      Seq.empty
    )
    // Combined keys + author + search_text + exact_keys + time window across key-value and key-only branches
    val catalogAndDate = Some(JsonBString("""{"catalog_path":null,"info_date":"2026-11-11"}"""))
    assertPage(
      query(
        keys = Some(JsonBString("""{"info_date":"2026-11-11"}""")),
        searchText = Some("CRM_CUSTOMERS")
      ),
      Seq(aqueduct2),
      expectedAuthor = "aqueduct"
    )
    assertPage(
      query(
        keys = Some(JsonBString("""{"catalog_path":null}""")),
        searchText = Some("CUSTOMER_PAYMENTS")
      ),
      Seq(unify1),
      expectedAuthor = "unify"
    )
    assertPage(
      query(
        keys = Some(JsonBString("""{"catalog_path":null}""")),
        from = Some(fromTime),
        to = Some(toTime),
        searchText = Some("CUSTOMER_PAYMENTS")
      ),
      Seq(unify1),
      expectedAuthor = "unify"
    )
    assertPage(
      query(
        keys = catalogAndDate,
        from = Some(fromTime),
        to = Some(toTime),
        exactKeys = Some(true),
        author = Some("aqueduct"),
        searchText = Some("CORE_PAYMENTS")
      ),
      Seq(aqueduct1),
      expectedAuthor = "aqueduct"
    )
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
        .zip(partitionings)
        .foreach { case (time, (id, _)) => insertCheckpoint(id, time) }
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
        query(keys = keys, from = Some(fromTime), limit = Some(2), offset = Some(2L)),
        partitionings.drop(2)
      )
      assertPage(query(keys = keys, from = Some(fromTime), limit = Some(2), offset = Some(3L)), Seq.empty)
    }
  }

  test("A time-only search reaches a partitioning after one thousand lower-ID matches") {
    val partitionings = (0 to 1000)
      .map { index =>
        val partitioning = insertPartitioning("time-page" -> index.toString)
        insertCheckpoint(partitioning._1, fromTime)
        partitioning
      }
      .sortBy(_._1)
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

  test("i_latest_first=true orders descending by ID with correct pagination and has_more") {
    val partitionings = (0 until 4).map(index => insertPartitioning("group" -> "latest-first", "row" -> index.toString))
    partitionings.foreach { case (id, _) => insertCheckpoint(id, fromTime) }
    val desc = partitionings.sortBy(-_._1)
    val keys = Some(JsonBString("""{"group":"latest-first"}"""))

    assertPage(
      query(keys = keys, limit = Some(2), offset = Some(0L), latestFirst = Some(true)),
      desc.take(2),
      hasMore = true,
      descending = true
    )
    assertPage(
      query(keys = keys, limit = Some(2), offset = Some(2L), latestFirst = Some(true)),
      desc.drop(2),
      hasMore = false,
      descending = true
    )
    assertPage(
      query(from = Some(fromTime), to = Some(toTime), limit = Some(2), offset = Some(0L), latestFirst = Some(true)),
      desc.take(2),
      hasMore = true,
      descending = true
    )
    assertPage(
      query(from = Some(fromTime), to = Some(toTime), limit = Some(2), offset = Some(2L), latestFirst = Some(true)),
      desc.drop(2),
      hasMore = false,
      descending = true
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

  private def queryBoolean(sql: String): Boolean = {
    val statement = dbConnection.connection.createStatement()
    try {
      val rs = statement.executeQuery(sql)
      assert(rs.next())
      rs.getBoolean(1)
    } finally {
      statement.close()
    }
  }

  private def hasExecute(role: String, fnSignature: String): Boolean =
    queryBoolean(s"SELECT has_function_privilege('$role', '$fnSignature', 'EXECUTE')")

  private def hasValidIndex(indexName: String): Boolean =
    queryBoolean(
      s"""SELECT EXISTS (
         |  SELECT 1
         |  FROM pg_index i
         |  JOIN pg_class c ON c.oid = i.indexrelid
         |  JOIN pg_namespace n ON n.oid = c.relnamespace
         |  WHERE n.nspname = 'runs'
         |    AND c.relname = '$indexName'
         |    AND i.indisvalid
         |    AND i.indisready
         |)""".stripMargin
    )

  private def assertErrorStatus51(search: DBFunction, expectedPrefix: String): Unit = {
    search.execute { rows =>
      assert(rows.hasNext)
      val row = rows.next()
      assert(row.getInt("status").contains(51))
      assert(row.getString("status_text").exists(_.startsWith(expectedPrefix)))
      assert(!rows.hasNext)
    }
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
    ("empty keys without time", () => query(keys = Some(JsonBString("{}"))), "At least one"),
    ("exact keys with NULL keys and time", () => query(from = Some(fromTime), exactKeys = Some(true)), "i_exact_keys"),
    (
      "exact keys with empty keys and time",
      () => query(keys = Some(JsonBString("{}")), from = Some(fromTime), exactKeys = Some(true)),
      "i_exact_keys"
    ),
    ("exact keys with author only", () => query(author = Some("aqueduct"), exactKeys = Some(true)), "i_exact_keys"),
    ("empty author", () => query(author = Some("")), "i_author"),
    ("whitespace-only author", () => query(author = Some("   \t\n")), "i_author"),
    ("whitespace-only author with keys", () => query(keys = pagingKeys, author = Some("   ")), "i_author"),
    ("empty search text", () => query(searchText = Some("")), "i_search_text"),
    ("whitespace-only search text", () => query(searchText = Some("   \t\n")), "i_search_text"),
    ("single-char search text", () => query(searchText = Some("a")), "i_search_text"),
    ("padded single-char search text", () => query(searchText = Some(" a ")), "i_search_text"),
    (
      "single-char search text with author",
      () => query(author = Some("aqueduct"), searchText = Some("a")),
      "i_search_text"
    )
  ).foreach { case (description, invalidQuery, message) =>
    test(s"Rejects $description with status 51") {
      assertErrorStatus51(invalidQuery(), message)
    }
  }

  test("Accepts 2-character search text and single-character v author") {
    val vAuthor = insertPartitioningByAuthor("v", "tag" -> "vv")
    assertPage(query(author = Some("v")), Seq(vAuthor), expectedAuthor = "v")
    assertPage(query(searchText = Some("vv")), Seq(vAuthor), expectedAuthor = "v")
  }

  test(
    "Privileges: execute for owner and atum_user on search_partitionings, owner-only on helper; search_path is pinned"
  ) {
    assert(queryBoolean(s"SELECT to_regprocedure('$searchSignature') IS NOT NULL"))
    assert(hasExecute("atum_owner", searchSignature))
    assert(hasExecute("atum_user", searchSignature))
    assert(!hasExecute("atum_reader", searchSignature))
    assert(!hasExecute("public", searchSignature))
    assert(
      queryBoolean(
        s"SELECT 'search_path=pg_catalog, pg_temp' = ANY(proconfig) " +
          s"FROM pg_proc WHERE oid = '$searchSignature'::regprocedure"
      )
    )

    assert(queryBoolean(s"SELECT to_regprocedure('$helperSignature') IS NOT NULL"))
    assert(hasExecute("atum_owner", helperSignature))
    assert(!hasExecute("atum_user", helperSignature))
    assert(!hasExecute("atum_reader", helperSignature))
    assert(!hasExecute("public", helperSignature))
    assert(
      queryBoolean(
        s"SELECT 'search_path=pg_catalog, pg_temp' = ANY(proconfig) " +
          s"FROM pg_proc WHERE oid = '$helperSignature'::regprocedure"
      )
    )
  }

  test("Indexes: partitionings_created_by_idx and partitionings_keys_to_values_idx exist and are valid") {
    assert(hasValidIndex("partitionings_created_by_idx"))
    assert(hasValidIndex("partitionings_keys_to_values_idx"))
  }

  Seq(
    "[]",
    "\"text\"",
    "42",
    "true",
    "null",
    """{"key":42}""",
    """{"key":true}""",
    """{"key":[]}""",
    """{"key":{}}""",
    """{"valid":"value","invalid":42}""",
    """{"valid":null,"invalid":42}"""
  ).foreach { invalidKeys =>
    test(s"Rejects non-string-or-null-map keys $invalidKeys even with a time filter with status 51") {
      assertErrorStatus51(
        query(keys = Some(JsonBString(invalidKeys)), from = Some(fromTime)),
        "i_keys must be a JSON object of string or null values"
      )
    }
  }
}

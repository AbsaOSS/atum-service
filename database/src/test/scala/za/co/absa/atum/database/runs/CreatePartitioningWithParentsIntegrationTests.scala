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
import za.co.absa.balta.classes.{JsonBString, QueryResult}
import za.co.absa.balta.classes.setter.CustomDBType

import java.time.OffsetDateTime

/**
 *  Tests of `runs.create_partitioning_with_parents(jsonb, text, bigint[], boolean)` (LINEAGE-03..06, POLICY-02/03).
 *
 *  Test-design pitfall: the advisory lock taken by `flows._link_to_parents` is transaction-scoped and `DBTestSuite`
 *  runs the whole test body in a single transaction (rolled back at the end). Once ANY slow-path call has happened
 *  in a test, the lock count of the session stays 1 until the rollback. Therefore:
 *   - fixture graphs are seeded WITHOUT the new function (`runs.create_partitioning` of the v1 path takes no lock),
 *   - every `advisoryLocksHeld() == 0` assertion precedes the first slow-path call of its test,
 *   - the slow-path test asserts `== 1` as its very last step.
 */
class CreatePartitioningWithParentsIntegrationTests extends DBTestSuite {

  private case class CreateResult(status: Int, statusText: String, id: Option[Long], createdBy: Option[String])

  private val fncCreatePartitioning = "runs.create_partitioning"
  private val fncCreateWithParents = "runs.create_partitioning_with_parents"
  private val fncGetAncestors = "runs.get_partitioning_ancestors"
  private val signature = "runs.create_partitioning_with_parents(jsonb,text,bigint[],boolean)"

  private val cycleText = "Parent partitioning would create a cycle"
  private val oldDate = OffsetDateTime.parse("2020-01-01T00:00:00Z")

  private val missingParentId = 987654321987L

  private def lineage(name: String): JsonBString = JsonBString(
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

  /** Seeds a partitioning via the v1 path (takes NO advisory lock); an optional parent is linked with it. */
  private def seed(p: JsonBString, parent: Option[Long] = None, author: String = "Seeder"): Long = {
    val call = function(fncCreatePartitioning)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", author)
    val withParent = parent match {
      case Some(id) => call.setParam("i_parent_partitioning_id", id)
      case None => call.setParamNull("i_parent_partitioning_id")
    }
    withParent.execute { qr =>
      assert(qr.hasNext)
      val row = qr.next()
      assert(row.getInt("status").exists(s => s == 11 || s == 12))
      row.getLong("id_partitioning").get
    }
  }

  private def readResult(qr: QueryResult): CreateResult = {
    assert(qr.hasNext)
    val row = qr.next()
    val result = CreateResult(
      row.getInt("status").get,
      row.getString("status_text").get,
      row.getLong("id_partitioning"),
      row.getString("created_by")
    )
    assert(!qr.hasNext)
    result
  }

  private def createMulti(
    p: JsonBString,
    parents: CustomDBType,
    copyMd: Boolean = false,
    user: String = "Fantômas"
  ): CreateResult = {
    function(fncCreateWithParents)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", user)
      .setParam("i_parent_partitioning_ids", parents)
      .setParam("i_copy_measure_definitions", copyMd)
      .execute(readResult)
  }

  private def createMultiNullParents(p: JsonBString): CreateResult = {
    function(fncCreateWithParents)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning_ids")
      .setParam("i_copy_measure_definitions", false)
      .execute(readResult)
  }

  private def createMultiNullFlag(p: JsonBString, parents: CustomDBType): CreateResult = {
    function(fncCreateWithParents)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_parent_partitioning_ids", parents)
      .setParamNull("i_copy_measure_definitions")
      .execute(readResult)
  }

  private def flowCount(id: Long): Long = table("flows.partitioning_to_flow").count(add("fk_partitioning", id))

  private def ancestorIds(id: Long): Set[Long] = {
    function(fncGetAncestors)
      .setParam("i_id_partitioning", id)
      .execute { qr =>
        Iterator
          .continually(qr)
          .takeWhile(_.hasNext)
          .flatMap(_.next().getLong("ancestor_id"))
          .toSet
      }
  }

  private def addMeasure(
    id: Long,
    name: String,
    columnsLiteral: String,
    author: String,
    createdAt: Option[OffsetDateTime] = None
  ): Unit = {
    val base = add("fk_partitioning", id)
      .add("created_by", author)
      .add("measure_name", name)
      .add("measured_columns", CustomDBType(columnsLiteral, "TEXT[]"))
    table("runs.measure_definitions").insert(createdAt.fold(base)(base.add("created_at", _)))
    ()
  }

  private def addAd(id: Long, key: String, value: String): Unit = {
    table("runs.additional_data").insert(
      add("fk_partitioning", id).add("created_by", "Seeder").add("ad_name", key).add("ad_value", value)
    )
    ()
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

  private def queryLong(sql: String): Long = {
    val statement = dbConnection.connection.createStatement()
    try {
      val rs = statement.executeQuery(sql)
      assert(rs.next())
      rs.getLong(1)
    } finally {
      statement.close()
    }
  }

  /** Advisory locks held by THIS session (the transaction-scoped lock is visible in pg_locks for our own backend). */
  private def advisoryLocksHeld(): Long = queryLong(
    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()"
  )

  private def hasExecute(role: String): Boolean =
    queryBoolean(s"SELECT has_function_privilege('$role', '$signature', 'EXECUTE')")

  private def fourTableCounts(): Seq[Long] = Seq(
    table("runs.partitionings").count(),
    table("flows.partitioning_to_flow").count(),
    table("runs.measure_definitions").count(),
    table("runs.additional_data").count()
  )

  // balta 0.1.0 binds multi-column NamedParams in the wrong order (https://github.com/AbsaOSS/balta/issues/2),
  // so two-column filters are expressed as SQL conditions; inputs are generated ids and test-constant names only
  private def measureOf(id: Long, name: String): String = s"fk_partitioning = $id AND measure_name = '$name'"

  private def measureCount(id: Long): Long = table("runs.measure_definitions").count(add("fk_partitioning", id))

  private def measureCount(id: Long, name: String): Long = table("runs.measure_definitions").count(measureOf(id, name))

  private def adCount(id: Long): Long = table("runs.additional_data").count(add("fk_partitioning", id))

  // ---- creation ----------------------------------------------------------------------------------------------

  test("Create with no parents: 11, the caller is the author, a single own flow and no advisory lock") {
    val result = createMulti(lineage("child"), ids(Seq.empty))

    assert(advisoryLocksHeld() == 0)
    assert(result.status == 11)
    assert(result.statusText == "Partitioning created")
    assert(result.createdBy.contains("Fantômas"))
    val child = result.id.get
    assert(flowCount(child) == 1)
    assert(ancestorIds(child).isEmpty)
  }

  test("Create with one parent: 12 and the parent is the only ancestor") {
    val p1 = seed(lineage("p1"))

    val result = createMulti(lineage("child"), ids(Seq(p1)))

    assert(result.status == 12)
    assert(result.statusText == "Partitioning created with parent partitioning")
    assert(result.createdBy.contains("Fantômas"))
    val child = result.id.get
    assert(flowCount(child) == 2)
    assert(ancestorIds(child) == Set(p1))
  }

  test("Create with N distinct parents: 12 and the child is a member of every parent's flow") {
    val p1 = seed(lineage("p1"))
    val p2 = seed(lineage("p2"))
    val p3 = seed(lineage("p3"))

    val result = createMulti(lineage("child"), ids(Seq(p1, p2, p3)))

    assert(result.status == 12)
    val child = result.id.get
    assert(flowCount(child) == 4)
    assert(ancestorIds(child) == Set(p1, p2, p3))
    Seq(p1, p2, p3).foreach(p => assert(flowCount(p) == 1))
  }

  test("Duplicate parent ids and a NULL element are ignored") {
    val p1 = seed(lineage("p1"))

    val result = createMulti(lineage("child"), ids(Seq(p1, p1, null)))

    assert(result.status == 12)
    assert(flowCount(result.id.get) == 2)
    assert(ancestorIds(result.id.get) == Set(p1))
  }

  test("A NULL parent array behaves like a parentless create") {
    val result = createMultiNullParents(lineage("child"))

    assert(advisoryLocksHeld() == 0)
    assert(result.status == 11)
    assert(flowCount(result.id.get) == 1)
  }

  test("An empty parent array behaves like a parentless create") {
    val result = createMulti(lineage("child"), CustomDBType("{}", "BIGINT[]"))

    assert(advisoryLocksHeld() == 0)
    assert(result.status == 11)
    assert(flowCount(result.id.get) == 1)
  }

  test("Existing partitioning with a NULL or empty parent array is a no-op: 14, no lock, nothing written") {
    val child = seed(lineage("child"), author = "Carol")
    val before = fourTableCounts()

    val withEmpty = createMulti(lineage("child"), ids(Seq.empty))
    assert(withEmpty.status == 14)
    assert(withEmpty.statusText == "Partitioning already present")
    assert(withEmpty.id.contains(child))

    val withNull = createMultiNullParents(lineage("child"))
    assert(withNull.status == 14)
    assert(withNull.id.contains(child))

    assert(fourTableCounts() == before)
    assert(advisoryLocksHeld() == 0)
  }

  // ---- idempotency and linking of existing partitionings -----------------------------------------------------

  test("Idempotent re-create by ANOTHER user: 14, nothing written, same id and the STORED author") {
    val p1 = seed(lineage("p1"))
    val p2 = seed(lineage("p2"))
    val first = createMulti(lineage("child"), ids(Seq(p1, p2)), user = "Alice")
    assert(first.status == 12)
    assert(first.createdBy.contains("Alice"))
    val before = fourTableCounts()

    val second = createMulti(lineage("child"), ids(Seq(p1, p2)), user = "Mallory")

    assert(second.status == 14)
    assert(second.statusText == "Partitioning already present")
    assert(second.id == first.id)
    assert(second.createdBy.contains("Alice"))
    assert(queryBoolean(s"SELECT created_by = 'Alice' FROM runs.partitionings WHERE id_partitioning = ${first.id.get}"))
    assert(fourTableCounts() == before)
  }

  test("Re-linking an already linked direct parent: 14, nothing written and no advisory lock") {
    val p1 = seed(lineage("p1"))
    val child = seed(lineage("child"), Some(p1))
    val before = fourTableCounts()

    val result = createMulti(lineage("child"), ids(Seq(p1)), user = "Mallory")

    assert(advisoryLocksHeld() == 0)
    assert(result.status == 14)
    assert(result.id.contains(child))
    assert(fourTableCounts() == before)
  }

  test("Re-linking a direct parent and a grandparent: 14, nothing written and no advisory lock") {
    val g = seed(lineage("g"))
    val p = seed(lineage("p"), Some(g))
    val child = seed(lineage("child"), Some(p))
    assert(ancestorIds(child) == Set(p, g))
    val before = fourTableCounts()

    val result = createMulti(lineage("child"), ids(Seq(p, g)))

    assert(advisoryLocksHeld() == 0)
    assert(result.status == 14)
    assert(result.id.contains(child))
    assert(fourTableCounts() == before)
  }

  test("Existing child gets a new parent: 13 and the child is a member of the parent's flow") {
    val p1 = seed(lineage("p1"))
    val child = seed(lineage("child"), author = "Carol")
    assert(ancestorIds(child).isEmpty)

    val result = createMulti(lineage("child"), ids(Seq(p1)))

    assert(result.status == 13)
    assert(result.statusText == "Partitioning parents registered")
    assert(result.id.contains(child))
    assert(result.createdBy.contains("Carol"))
    assert(flowCount(child) == 2)
    assert(ancestorIds(child) == Set(p1))
  }

  test("Existing child with a mix of linked and new parents: 13 and all of them become ancestors") {
    val p1 = seed(lineage("p1"))
    val p2 = seed(lineage("p2"))
    val child = seed(lineage("child"), Some(p1))
    assert(ancestorIds(child) == Set(p1))

    val result = createMulti(lineage("child"), ids(Seq(p1, p2)))

    assert(result.status == 13)
    assert(result.id.contains(child))
    assert(ancestorIds(child) == Set(p1, p2))
  }

  // ---- cycles and missing parents ----------------------------------------------------------------------------

  test("A partitioning cannot be its own parent: 32 naming the id, nothing written") {
    val child = seed(lineage("child"), author = "Carol")
    val before = fourTableCounts()

    val result = createMulti(lineage("child"), ids(Seq(child)))

    assert(result.status == 32)
    assert(result.statusText == s"$cycleText: $child")
    assert(result.id.isEmpty)
    assert(result.createdBy.isEmpty)
    assert(fourTableCounts() == before)
    assert(ancestorIds(child).isEmpty)
  }

  test("A direct descendant as a parent: 32, nothing written (partitionings, flows, measures, additional data)") {
    val c = seed(lineage("C"))
    val d = seed(lineage("D"), Some(c))
    addMeasure(d, "m_d", """{"col"}""", "Dave")
    addAd(d, "k_d", "x")
    val before = fourTableCounts()

    val result = createMulti(lineage("C"), ids(Seq(d)), copyMd = true)

    assert(result.status == 32)
    assert(result.statusText == s"$cycleText: $d")
    assert(result.id.isEmpty)
    assert(fourTableCounts() == before)
    assert(ancestorIds(c).isEmpty)
    assert(ancestorIds(d) == Set(c))
  }

  test("An indirect descendant as a parent (A -> B -> C, A under C): 32, nothing written") {
    val a = seed(lineage("A"))
    val b = seed(lineage("B"), Some(a))
    val c = seed(lineage("C"), Some(b))
    assert(ancestorIds(c) == Set(a, b))
    val before = fourTableCounts()

    val result = createMulti(lineage("A"), ids(Seq(c)))

    assert(result.status == 32)
    assert(result.statusText == s"$cycleText: $c")
    assert(fourTableCounts() == before)
    assert(ancestorIds(a).isEmpty)
  }

  test("A valid parent together with a cycle-making one: 32 and nothing is written for the valid one") {
    val ok = seed(lineage("ok"))
    val c = seed(lineage("C"))
    val d = seed(lineage("D"), Some(c))
    val before = fourTableCounts()

    val result = createMulti(lineage("C"), ids(Seq(ok, d)))

    assert(result.status == 32)
    assert(result.statusText == s"$cycleText: $d")
    assert(fourTableCounts() == before)
    assert(ancestorIds(c).isEmpty)
  }

  test("Several offending parents are all named, comma separated in ascending order") {
    val c = seed(lineage("C"))
    val d = seed(lineage("D"), Some(c))
    val e = seed(lineage("E"), Some(c))
    val before = fourTableCounts()

    val result = createMulti(lineage("C"), ids(Seq(e, d)))

    assert(result.status == 32)
    assert(result.statusText == s"$cycleText: ${Seq(d, e).sorted.mkString(",")}")
    assert(fourTableCounts() == before)
  }

  test("Unknown parent of a new child: 41 and the child is NOT created") {
    val p1 = seed(lineage("p1"))
    val before = fourTableCounts()

    val result = createMulti(lineage("child"), ids(Seq(p1, missingParentId)))

    assert(result.status == 41)
    assert(result.statusText == "Parent partitioning not found")
    assert(result.id.isEmpty)
    assert(result.createdBy.isEmpty)
    assert(table("runs.partitionings").count(add("partitioning", lineage("child"))) == 0)
    assert(fourTableCounts() == before)
  }

  test("Unknown parent of an existing child: 41 and nothing is linked") {
    val p2 = seed(lineage("p2"))
    val child = seed(lineage("child"))
    val before = fourTableCounts()

    val result = createMulti(lineage("child"), ids(Seq(p2, missingParentId)))

    assert(result.status == 41)
    assert(result.id.isEmpty)
    assert(fourTableCounts() == before)
    assert(ancestorIds(child).isEmpty)
  }

  // ---- measure definitions (POLICY-03) -----------------------------------------------------------------------

  test("Measure copy flag false: nothing is copied to a new child") {
    val p1 = seed(lineage("p1"))
    addMeasure(p1, "m1", """{"col1"}""", "Alice")

    val result = createMulti(lineage("child"), ids(Seq(p1)), copyMd = false)

    assert(result.status == 12)
    assert(measureCount(result.id.get) == 0)
  }

  test("Measure copy flag NULL is treated as false") {
    val p1 = seed(lineage("p1"))
    addMeasure(p1, "m1", """{"col1"}""", "Alice")

    val result = createMultiNullFlag(lineage("child"), ids(Seq(p1)))

    assert(result.status == 12)
    assert(measureCount(result.id.get) == 0)
  }

  test("Measure copy flag true with one parent copies its measure definitions") {
    val p1 = seed(lineage("p1"))
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addMeasure(p1, "m2", """{"col2"}""", "Alice")

    val result = createMulti(lineage("child"), ids(Seq(p1)), copyMd = true)

    assert(result.status == 12)
    val child = result.id.get
    assert(measureCount(child) == 2)
    assert(measureCount(child, "m1") == 1)
    assert(measureCount(child, "m2") == 1)
  }

  test("Measure copy flag true with N parents copies the distinct union by (name, columns); column order counts") {
    val p1 = seed(lineage("p1"))
    val p2 = seed(lineage("p2"))
    addMeasure(p1, "m_shared", """{"x"}""", "Alice")
    addMeasure(p1, "m_ab", """{"a","b"}""", "Alice")
    addMeasure(p2, "m_shared", """{"x"}""", "Bob")
    addMeasure(p2, "m_ab", """{"b","a"}""", "Bob")
    addMeasure(p2, "m_only2", """{"y"}""", "Bob")

    val result = createMulti(lineage("child"), ids(Seq(p1, p2)), copyMd = true)

    assert(result.status == 12)
    val child = result.id.get
    assert(measureCount(child) == 4)
    assert(measureCount(child, "m_shared") == 1)
    assert(measureCount(child, "m_ab") == 2)
    assert(measureCount(child, "m_only2") == 1)
  }

  test("Copied measure definitions are authored by the caller and stamped with now(), not the parent's") {
    val p1 = seed(lineage("p1"))
    addMeasure(p1, "m1", """{"col1"}""", "Alice", Some(oldDate))

    val result = createMulti(lineage("child"), ids(Seq(p1)), copyMd = true, user = "Caller")

    val child = result.id.get
    assert(measureCount(child, "m1") == 1)
    val condition = s"fk_partitioning = $child AND measure_name = 'm1'"
    assert(queryBoolean(s"SELECT created_by = 'Caller' FROM runs.measure_definitions WHERE $condition"))
    assert(queryBoolean(s"SELECT created_at = now() FROM runs.measure_definitions WHERE $condition"))
    assert(
      queryBoolean(
        s"SELECT created_at <> '2020-01-01T00:00:00Z'::timestamptz FROM runs.measure_definitions WHERE $condition"
      )
    )
    assert(queryBoolean(s"SELECT created_by = 'Alice' FROM runs.measure_definitions WHERE fk_partitioning = $p1"))
  }

  test("Measure copy flag true on an EXISTING child copies nothing, also when the link is new") {
    val p1 = seed(lineage("p1"))
    addMeasure(p1, "m_parent", """{"col1"}""", "Alice")
    val child = seed(lineage("child"))
    addMeasure(child, "m_own", """{"own"}""", "Carol")

    val result = createMulti(lineage("child"), ids(Seq(p1)), copyMd = true)

    assert(result.status == 13)
    assert(measureCount(child) == 1)
    assert(measureCount(child, "m_own") == 1)
    assert(measureCount(child, "m_parent") == 0)
  }

  test("Measure copy flag true on an existing child that is already linked copies nothing (14)") {
    val p1 = seed(lineage("p1"))
    val child = seed(lineage("child"), Some(p1))
    addMeasure(p1, "m_later", """{"col1"}""", "Alice")

    val result = createMulti(lineage("child"), ids(Seq(p1)), copyMd = true)

    assert(result.status == 14)
    assert(measureCount(child, "m_later") == 0)
  }

  // ---- additional data (POLICY-02) ---------------------------------------------------------------------------

  test("Additional data is never copied to a new child, even with the measure copy flag on") {
    val p1 = seed(lineage("p1"))
    val p2 = seed(lineage("p2"))
    addAd(p1, "k1", "v1")
    addAd(p2, "k2", "v2")
    addMeasure(p1, "m1", """{"col1"}""", "Alice")

    val result = createMulti(lineage("child"), ids(Seq(p1, p2)), copyMd = true)

    assert(result.status == 12)
    val child = result.id.get
    assert(adCount(child) == 0)
    assert(measureCount(child, "m1") == 1)
  }

  test("Additional data is never copied to an existing child, linked now or already") {
    val p1 = seed(lineage("p1"))
    val p2 = seed(lineage("p2"))
    addAd(p1, "k1", "v1")
    addAd(p2, "k2", "v2")
    val child = seed(lineage("child"), Some(p1))

    val linked = createMulti(lineage("child"), ids(Seq(p1, p2)), copyMd = true)
    assert(linked.status == 13)
    assert(adCount(child) == 0)

    val again = createMulti(lineage("child"), ids(Seq(p1, p2)), copyMd = true)
    assert(again.status == 14)
    assert(adCount(child) == 0)
  }

  // ---- lineage shapes ----------------------------------------------------------------------------------------

  test("Diamond via create: D under (B, E) where B is under A gets all of them as ancestors") {
    val a = seed(lineage("A"))
    val b = seed(lineage("B"), Some(a))
    val e = seed(lineage("E"))

    val result = createMulti(lineage("D"), ids(Seq(b, e)))

    assert(result.status == 12)
    val d = result.id.get
    assert(ancestorIds(d) == Set(a, b, e))
    assert(flowCount(d) == 4)
  }

  test("Descendants of an existing child become reachable from a newly linked parent (WR-05)") {
    val n = seed(lineage("N"))
    val c = seed(lineage("C"))
    val d = seed(lineage("D"), Some(c))
    val e = seed(lineage("E"), Some(d))
    assert(ancestorIds(e) == Set(c, d))

    val result = createMulti(lineage("C"), ids(Seq(n)))

    assert(result.status == 13)
    assert(ancestorIds(c) == Set(n))
    assert(ancestorIds(d) == Set(c, n))
    assert(ancestorIds(e) == Set(c, d, n))

    val flowsBefore = table("flows.partitioning_to_flow").count()
    val again = createMulti(lineage("C"), ids(Seq(n)))
    assert(again.status == 14)
    assert(table("flows.partitioning_to_flow").count() == flowsBefore)
  }

  // ---- privileges, removed objects, locking ------------------------------------------------------------------

  test("Privileges: execute for the owner and atum_user only; search_path is pinned") {
    assert(hasExecute("atum_owner"))
    assert(hasExecute("atum_user"))
    assert(!hasExecute("atum_reader"))
    assert(!hasExecute("public"))
    assert(
      queryBoolean(
        s"SELECT coalesce(proconfig::text, '') LIKE '%search_path=pg_catalog, public, pg_temp%' " +
          s"FROM pg_proc WHERE oid = '$signature'::regprocedure"
      )
    )
  }

  test("The old 3-argument function and the additional data copy helper do not exist") {
    assert(queryBoolean("SELECT to_regprocedure('runs.create_partitioning_with_parents(jsonb,text,bigint[])') IS NULL"))
    assert(
      queryBoolean(
        "SELECT to_regprocedure('runs._copy_parents_data_to_partitioning(bigint,bigint[],bigint[],text)') IS NULL"
      )
    )
    assert(queryBoolean(s"SELECT to_regprocedure('$signature') IS NOT NULL"))
  }

  test("Shipped runs.create_partitioning is untouched: 2-arg, named and positional untyped NULL calls resolve") {
    val p1 = seed(lineage("p1"))

    val child = function(fncCreatePartitioning)
      .setParam("i_partitioning", lineage("child"))
      .setParam("i_by_user", "Fantômas")
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        assert(row.getInt("status").contains(11))
        row.getLong("id_partitioning").get
      }

    function(fncCreatePartitioning)
      .setParam("i_partitioning", lineage("child"))
      .setParam("i_by_user", "Fantômas")
      .setParam("i_parent_partitioning_id", p1)
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        assert(row.getInt("status").contains(31))
        assert(row.getLong("id_partitioning").contains(child))
      }

    assert(flowCount(child) == 1)

    // positional call with an untyped NULL literal must not be ambiguous
    val statement = dbConnection.connection.createStatement()
    try {
      val rs = statement.executeQuery(
        """SELECT status, status_text FROM runs.create_partitioning(
          |  '{"version": 1, "keys": ["pn"], "keysToValuesMap": {"pn": "1"}}'::jsonb, 'Fantômas', NULL)""".stripMargin
      )
      assert(rs.next())
      assert(rs.getInt("status") == 11)
      assert(!rs.next())
    } finally {
      statement.close()
    }

    assert(
      queryBoolean(
        "SELECT count(*) = 1 FROM pg_proc WHERE proname = 'create_partitioning' AND pronamespace = 'runs'::regnamespace"
      )
    )
  }

  test("Slow path (new child with a parent) takes exactly one advisory lock") {
    // runs.create_partitioning_with_parents is the only place of this suite that goes through the slow path here
    val p1 = seed(lineage("p1"))
    assert(advisoryLocksHeld() == 0)

    val result = createMulti(lineage("child"), ids(Seq(p1)))

    assert(result.status == 12)
    assert(advisoryLocksHeld() == 1)
  }

}

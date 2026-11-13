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
import za.co.absa.balta.classes.setter.CustomDBType

class UpdatePartitioningParentIntegrationTests extends DBTestSuite {

  private val updateParentFn = "runs.update_partitioning_parent"
  private val createPartitioningFn = "runs.create_partitioning"
  private val fncGetPartitioningAdditionalData = "runs.get_partitioning_additional_data"
  private val fncGetPartitioningMeasuresById = "runs.get_partitioning_measures_by_id"

  private val partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3", "key2", "key4"],
      |  "keysToValuesMap": {
      |    "key1": "valueX",
      |    "key2": "valueY",
      |    "key3": "valueZ",
      |    "key4": "valueA"
      |  }
      |}
      |""".stripMargin
  )

  private val parentPartitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3"],
      |  "keysToValuesMap": {
      |    "key1": "valueX",
      |    "key3": "valueZ"
      |  }
      |}
      |""".stripMargin
  )

  // ---- Part 1: original single-scenario test ------------------------------------------------------------------
  // Legacy style, predates the helper set used by Part 2 below. `addMeasure`/`addAd` (defined in Part 2) are
  // reused here since they already capture this exact fixture-row shape; no new duplication is added.

  private def createPartitioning(p: JsonBString, author: String): Long =
    function(createPartitioningFn)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", author)
      .execute { queryResult =>
        queryResult.next().getLong("id_partitioning").get
      }

  //Data Preparation Step
  def dataPreparation(): (Long, Long, Long) = {
    val nonExistentID = 9999L

    val parentPartitioningIDTest = createPartitioning(parentPartitioning, "Albert Einstein")
    val childPartitioningIDTest = createPartitioning(partitioning, "Albert Einstein")

    addAd(parentPartitioningIDTest, "ad_1", "This is the additional data for Joseph")
    addAd(parentPartitioningIDTest, "ad_2", "This is the additional data for Joseph")
    addMeasure(parentPartitioningIDTest, "measure1", """{"col1"}""")
    addMeasure(parentPartitioningIDTest, "measure2", """{"col2"}""")

    (nonExistentID, parentPartitioningIDTest, childPartitioningIDTest)
  }

  //Update Parent with additional data and with measurements
  test("Parent Partitioning Updated with additional data and with measurements") {

    val (nonExistentID, parentPartitioningIDTest, childPartitioningIDTest) = dataPreparation()

    function(updateParentFn)
      .setParam("i_id_partitioning", childPartitioningIDTest)
      .setParam("i_id_parent_partitioning", parentPartitioningIDTest)
      .setParam("i_by_user", "Happy Nappy")
      .setParam("i_copy_measurements", true)
      .setParam("i_copy_additional_data", true)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("OK"))
        assert(!queryResult.hasNext)
      }

    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", parentPartitioningIDTest)) == 1
    )
    assert(
      table("flows.partitioning_to_flow").count(add("fk_partitioning", childPartitioningIDTest)) == 2
    )

    function(fncGetPartitioningAdditionalData)
      .setParam("i_partitioning_id", childPartitioningIDTest)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("OK"))
        assert(results.getString("ad_name").contains("ad_1"))
        assert(results.getString("ad_value").contains("This is the additional data for Joseph"))
        assert(results.getString("ad_author").contains("Happy Nappy"))

        val results2 = queryResult.next()
        assert(results2.getInt("status").contains(11))
        assert(results2.getString("status_text").contains("OK"))
        assert(results2.getString("ad_name").contains("ad_2"))
        assert(results2.getString("ad_value").contains("This is the additional data for Joseph"))
        assert(results2.getString("ad_author").contains("Happy Nappy"))
        assert(!queryResult.hasNext)
      }

    function(fncGetPartitioningMeasuresById)
      .setParam("i_partitioning_id", childPartitioningIDTest)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("OK"))
        assert(results.getString("measure_name").contains("measure1"))
        assert(results.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col1")))

        val results2 = queryResult.next()
        assert(results2.getInt("status").contains(11))
        assert(results2.getString("status_text").contains("OK"))
        assert(results2.getString("measure_name").contains("measure2"))
        assert(results2.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col2")))
        assert(!queryResult.hasNext)
      }
  }

  // ---- Part 2: shared linking routine, cycle rejection, copy policy ---------------------------------------------
  //
  // Test-design pitfall: the advisory lock taken by `flows._link_to_parents` is transaction-scoped and `DBTestSuite`
  // runs the whole test body in one transaction (rolled back at the end). After ANY slow-path call the lock count of
  // the session stays 1 until the rollback. Therefore fixture links are seeded WITHOUT the linking routine
  // (`runs.create_partitioning` of the v1 path takes no lock) and every `advisoryLocksHeld() == 0` assertion
  // precedes the first slow-path call of its test.

  private case class PatchResult(status: Int, statusText: String)

  private val signature = "runs.update_partitioning_parent(bigint,bigint,text,boolean,boolean)"
  private val cycleText = "Parent partitioning would create a cycle"
  private val unknownId = 987654321987L
  private val parentMeasures = Seq("measure1", "measure2")
  private val parentAdNames = Seq("ad_1", "ad_2")

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

  /** Seeds a partitioning via the v1 path (takes NO advisory lock); an optional parent is linked with it. */
  private def seed(name: String, parent: Option[Long] = None): Long = {
    val call = function(createPartitioningFn)
      .setParam("i_partitioning", lineage(name))
      .setParam("i_by_user", "Seeder")
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

  private def addMeasure(id: Long, name: String, columnsLiteral: String, author: String = "Joseph"): Unit = {
    table("runs.measure_definitions").insert(
      add("fk_partitioning", id)
        .add("created_by", author)
        .add("measure_name", name)
        .add("measured_columns", CustomDBType(columnsLiteral, "TEXT[]"))
    )
    ()
  }

  private def addAd(id: Long, key: String, value: String): Unit = {
    table("runs.additional_data").insert(
      add("fk_partitioning", id).add("created_by", "Joseph").add("ad_name", key).add("ad_value", value)
    )
    ()
  }

  /** A seeded parent carrying the two measure definitions and the two additional data records of `dataPreparation`. */
  private def seedParentWithData(name: String): Long = {
    val parent = seed(name)
    addMeasure(parent, "measure1", """{"col1"}""")
    addMeasure(parent, "measure2", """{"col2"}""")
    addAd(parent, "ad_1", "This is the additional data for Joseph")
    addAd(parent, "ad_2", "This is the additional data for Joseph")
    parent
  }

  /** Calls the function; a flag given as `None` is NOT bound, so the SQL default (true) applies. */
  private def patchOpt(
    child: Long,
    parent: Long,
    copyMeasurements: Option[Boolean],
    copyAdditionalData: Option[Boolean],
    user: String = "Happy Nappy"
  ): PatchResult = {
    val base = function(updateParentFn)
      .setParam("i_id_partitioning", child)
      .setParam("i_id_parent_partitioning", parent)
      .setParam("i_by_user", user)
    val withMeasurements = copyMeasurements.fold(base)(base.setParam("i_copy_measurements", _))
    val withAdditionalData =
      copyAdditionalData.fold(withMeasurements)(withMeasurements.setParam("i_copy_additional_data", _))
    withAdditionalData.execute { qr =>
      assert(qr.hasNext)
      val row = qr.next()
      val result = PatchResult(row.getInt("status").get, row.getString("status_text").get)
      assert(!qr.hasNext)
      result
    }
  }

  private def patch(child: Long, parent: Long, copyMeasurements: Boolean, copyAdditionalData: Boolean): PatchResult =
    patchOpt(child, parent, Some(copyMeasurements), Some(copyAdditionalData))

  private def fourTableCounts(): Seq[Long] = Seq(
    table("runs.partitionings").count(),
    table("flows.partitioning_to_flow").count(),
    table("runs.measure_definitions").count(),
    table("runs.additional_data").count()
  )

  private def measureCount(id: Long): Long = table("runs.measure_definitions").count(add("fk_partitioning", id))

  private def adCount(id: Long): Long = table("runs.additional_data").count(add("fk_partitioning", id))

  private def flowCount(id: Long): Long = table("flows.partitioning_to_flow").count(add("fk_partitioning", id))

  private def queryStrings(sql: String): Seq[String] = {
    val statement = dbConnection.connection.createStatement()
    try {
      val rs = statement.executeQuery(sql)
      Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toList
    } finally {
      statement.close()
    }
  }

  private def queryLong(sql: String): Long = queryStrings(sql).head.toLong

  private def queryBoolean(sql: String): Boolean = Set("t", "true").contains(queryStrings(sql).head)

  private def measureNames(id: Long): Seq[String] =
    queryStrings(s"SELECT measure_name FROM runs.measure_definitions WHERE fk_partitioning = $id ORDER BY 1")

  private def adNames(id: Long): Seq[String] =
    queryStrings(s"SELECT ad_name FROM runs.additional_data WHERE fk_partitioning = $id ORDER BY 1")

  /** Advisory locks held by THIS session (the transaction-scoped lock is visible in pg_locks for our own backend). */
  private def advisoryLocksHeld(): Long = queryLong(
    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()"
  )

  private def ancestorIds(id: Long): Set[Long] = {
    function("lineage.get_partitioning_ancestors")
      .setParam("i_id_partitioning", id)
      .execute { qr =>
        Iterator
          .continually(qr)
          .takeWhile(_.hasNext)
          .flatMap(_.next().getLong("ancestor_id"))
          .toSet
      }
  }

  // ---- rejections: 32 before any write, 41, 42 ------------------------------------------------------------------

  test("Indirect cycle A -> B -> C, linking A under C, is rejected with 32 naming C") {
    val a = seed("indirect-a")
    val b = seed("indirect-b", Some(a))
    val c = seed("indirect-c", Some(b))
    addMeasure(c, "child_measure", """{"colC"}""")
    addAd(c, "child_ad", "child value")
    val before = fourTableCounts()

    val result = patch(a, c, copyMeasurements = true, copyAdditionalData = true)

    assert(result.status == 32)
    assert(result.statusText == s"$cycleText: $c")
    assert(fourTableCounts() == before)
    assert(measureCount(a) == 0)
    assert(adCount(a) == 0)
    assert(ancestorIds(a).isEmpty)
    assert(ancestorIds(c) == Set(a, b))
  }

  test("Child missing: 41 and nothing is written (both flags true)") {
    val parent = seedParentWithData("missing-child")
    val before = fourTableCounts()

    val result = patch(unknownId, parent, copyMeasurements = true, copyAdditionalData = true)

    assert(result.status == 41)
    assert(result.statusText == "Child Partitioning not found")
    assert(fourTableCounts() == before)
    assert(advisoryLocksHeld() == 0)
  }

  test("Parent missing: 42 and nothing is written (both flags true)") {
    val child = seed("missing-parent")
    val before = fourTableCounts()

    val result = patch(child, unknownId, copyMeasurements = true, copyAdditionalData = true)

    assert(result.status == 42)
    assert(result.statusText == "Parent Partitioning not found")
    assert(fourTableCounts() == before)
    assert(advisoryLocksHeld() == 0)
  }

  // ---- linking through the shared routine ---------------------------------------------------------------------

  test("Link only (flags false/false): linked, nothing copied, 11 OK") {
    val parent = seedParentWithData("link-only-parent")
    val child = seed("link-only-child")

    val result = patch(child, parent, copyMeasurements = false, copyAdditionalData = false)

    assert(result.status == 11)
    assert(result.statusText == "OK")
    assert(ancestorIds(child) == Set(parent))
    assert(measureCount(child) == 0)
    assert(adCount(child) == 0)
  }

  test("A leaf gets the grandparents through PATCH; once it has descendants a new parent is refused with 33") {
    val a = seed("prop-a")
    val b = seed("prop-b", Some(a))
    val c = seed("prop-c")
    val n = seedParentWithData("prop-n")

    assert(patch(c, b, copyMeasurements = false, copyAdditionalData = false).status == 11)
    assert(ancestorIds(c) == Set(a, b))

    val d = seed("prop-d", Some(c))
    val before = fourTableCounts()

    val result = patch(c, n, copyMeasurements = true, copyAdditionalData = true)

    assert(result.status == 33)
    assert(result.statusText.contains("descendants"))
    assert(fourTableCounts() == before)
    assert(ancestorIds(c) == Set(a, b))
    assert(ancestorIds(d) == Set(a, b, c))
    assert(measureCount(c) == 0)
    assert(adCount(c) == 0)
  }

  test("A child with descendants stays a no-op (11, no new link) when the parent is already its ancestor") {
    val a = seed("noop-desc-a")
    val c = seed("noop-desc-c", Some(a))
    val d = seed("noop-desc-d", Some(c))
    val before = fourTableCounts()

    val result = patch(c, a, copyMeasurements = false, copyAdditionalData = false)

    assert(result.status == 11)
    assert(fourTableCounts() == before)
    assert(ancestorIds(d) == Set(a, c))
    assert(advisoryLocksHeld() == 0)
  }

  test("Copies are applied on a link that is already in place (POLICY-04), no new link is written") {
    val parent = seedParentWithData("noop-parent")
    val child = seed("noop-child", Some(parent))
    val flowRowsBefore = table("flows.partitioning_to_flow").count()
    assert(ancestorIds(child) == Set(parent))

    assert(advisoryLocksHeld() == 0)
    val result = patch(child, parent, copyMeasurements = true, copyAdditionalData = true)

    assert(result.status == 11)
    assert(result.statusText == "OK")
    assert(measureNames(child) == parentMeasures)
    assert(adNames(child) == parentAdNames)
    assert(table("flows.partitioning_to_flow").count() == flowRowsBefore)
    assert(advisoryLocksHeld() == 0)
  }

  test("A repeated PATCH with copyMeasurements=true is a no-op returning 11, not a unique violation") {
    val parent = seedParentWithData("repeat-parent")
    val child = seed("repeat-child")

    val first = patch(child, parent, copyMeasurements = true, copyAdditionalData = false)
    assert(first.status == 11)
    assert(first.statusText == "OK")
    val afterFirst = fourTableCounts()
    assert(measureCount(child) == 2)

    val second = patch(child, parent, copyMeasurements = true, copyAdditionalData = false)
    assert(second.status == 11)
    assert(second.statusText == "OK")
    assert(fourTableCounts() == afterFirst)
    assert(measureNames(child) == parentMeasures)
  }

  test("The child's own measure definitions win: the conflicting row is untouched, the rest is copied") {
    val parent = seedParentWithData("own-parent")
    val child = seed("own-child")
    addMeasure(child, "measure1", """{"col1"}""", "ChildOwner")
    val ownRowSql = "SELECT id_measure_definition || '|' || created_by || '|' || created_at " +
      s"FROM runs.measure_definitions WHERE fk_partitioning = $child AND measure_name = 'measure1'"
    val ownRowBefore = queryStrings(ownRowSql)
    assert(ownRowBefore.size == 1)

    val result = patch(child, parent, copyMeasurements = true, copyAdditionalData = false)

    assert(result.status == 11)
    assert(queryStrings(ownRowSql) == ownRowBefore)
    assert(ownRowBefore.head.contains("|ChildOwner|"))
    assert(measureNames(child) == parentMeasures)
    assert(measureCount(child) == 2)
    assert(
      queryStrings(
        s"SELECT created_by FROM runs.measure_definitions WHERE fk_partitioning = $child AND measure_name = 'measure2'"
      ) == Seq("Happy Nappy")
    )
  }

  // ---- copy flags: additional data stays opt-in on PATCH ---------------------------------------------

  test("Flags (true, false) copy the measure definitions but not the additional data") {
    val parent = seedParentWithData("flags-tf-parent")
    val child = seed("flags-tf-child")

    val result = patch(child, parent, copyMeasurements = true, copyAdditionalData = false)

    assert(result.status == 11)
    assert(measureNames(child) == parentMeasures)
    assert(adCount(child) == 0)
  }

  test("Flags (false, true) copy the additional data but not the measure definitions") {
    val parent = seedParentWithData("flags-ft-parent")
    val child = seed("flags-ft-child")

    val result = patch(child, parent, copyMeasurements = false, copyAdditionalData = true)

    assert(result.status == 11)
    assert(measureCount(child) == 0)
    assert(adNames(child) == parentAdNames)
  }

  test("Omitting both flags behaves as (true, true): the current defaults are kept") {
    val parent = seedParentWithData("flags-default-parent")
    val child = seed("flags-default-child")

    val result = patchOpt(child, parent, None, None)

    assert(result.status == 11)
    assert(result.statusText == "OK")
    assert(measureNames(child) == parentMeasures)
    assert(adNames(child) == parentAdNames)
  }

  test("Additional data copy keeps its semantics: the parent's value overwrites the child's key of the same name") {
    val parent = seedParentWithData("ad-overwrite-parent")
    val child = seed("ad-overwrite-child")
    addAd(child, "ad_1", "own value of the child")
    addAd(child, "ad_own", "stays")

    val result = patch(child, parent, copyMeasurements = false, copyAdditionalData = true)

    assert(result.status == 11)
    assert(adNames(child) == Seq("ad_1", "ad_2", "ad_own"))
    assert(
      queryStrings(
        s"SELECT ad_value FROM runs.additional_data WHERE fk_partitioning = $child AND ad_name = 'ad_1'"
      ) == Seq("This is the additional data for Joseph")
    )
  }

  // ---- privileges, configuration ---------------------------------------------------------------------------------

  test("Privileges and function configuration: atum_user may execute, the search_path is pinned") {
    assert(queryBoolean(s"SELECT has_function_privilege('atum_user', '$signature', 'EXECUTE')"))
    assert(
      queryBoolean(
        s"SELECT coalesce(proconfig::text, '') LIKE '%search_path=pg_catalog, public, pg_temp%' " +
          s"FROM pg_proc WHERE oid = '$signature'::regprocedure"
      )
    )
  }
}

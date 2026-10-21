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

class CopyParentsDataToPartitioningIntegrationTests extends DBTestSuite {

  private val fncCopyParentsData = "runs._copy_parents_data_to_partitioning"
  private val fncCreatePartitioning = "runs.create_partitioning"

  private val parent1Partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["pkey1"],
      |  "keysToValuesMap": {
      |    "pkey1": "valueP1"
      |  }
      |}
      |""".stripMargin
  )

  private val parent2Partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["pkey2"],
      |  "keysToValuesMap": {
      |    "pkey2": "valueP2"
      |  }
      |}
      |""".stripMargin
  )

  private val childPartitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["pkey1", "ckey"],
      |  "keysToValuesMap": {
      |    "pkey1": "valueP1",
      |    "ckey": "valueC"
      |  }
      |}
      |""".stripMargin
  )

  private def createPartitioning(p: JsonBString, author: String): Long = {
    function(fncCreatePartitioning)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", author)
      .setParamNull("i_parent_partitioning_id")
      .execute { qr =>
        val row = qr.next()
        assert(row.getInt("status").contains(11))
        row.getLong("id_partitioning").get
      }
  }

  private def createAll(): (Long, Long, Long) = {
    val p1 = createPartitioning(parent1Partitioning, "Alice")
    val p2 = createPartitioning(parent2Partitioning, "Bob")
    val child = createPartitioning(childPartitioning, "Carol")
    (p1, p2, child)
  }

  private def addMeasure(
    id: Long,
    name: String,
    columnsLiteral: String,
    author: String,
    createdAt: Option[String] = None
  ): Unit = {
    val base = add("fk_partitioning", id)
      .add("created_by", author)
      .add("measure_name", name)
      .add("measured_columns", CustomDBType(columnsLiteral, "TEXT[]"))
    val params = createdAt match {
      case Some(ts) => base.add("created_at", CustomDBType(ts, "TIMESTAMPTZ"))
      case None => base
    }
    table("runs.measure_definitions").insert(params)
    ()
  }

  private def hasExecute(role: String, signature: String): Boolean = {
    val statement = dbConnection.connection.createStatement()
    try {
      val rs = statement.executeQuery(s"SELECT has_function_privilege('$role', '$signature', 'EXECUTE')")
      assert(rs.next())
      rs.getBoolean(1)
    } finally {
      statement.close()
    }
  }

  private def addAd(id: Long, key: String, value: Option[String]): Unit = {
    val base = add("fk_partitioning", id).add("created_by", "Seeder").add("ad_name", key)
    val params = value match {
      case Some(v) => base.add("ad_value", v)
      case None => base.addNull("ad_value")
    }
    table("runs.additional_data").insert(params)
    ()
  }

  private def ids(xs: Seq[Any]): CustomDBType =
    CustomDBType(xs.map(x => if (x == null) "NULL" else x.toString).mkString("{", ",", "}"), "BIGINT[]")

  private def copy(child: Long, scope: CustomDBType, copyFrom: CustomDBType): (Int, String, Seq[String], Int) = {
    function(fncCopyParentsData)
      .setParam("i_id_partitioning", child)
      .setParam("i_conflict_scope_parent_ids", scope)
      .setParam("i_copy_from_parent_ids", copyFrom)
      .setParam("i_by_user", "Copier")
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        val result = (
          row.getInt("status").get,
          row.getString("status_text").get,
          row.getArray[String]("skipped_ad_keys").map(_.toSeq).get,
          row.getInt("copied_rows").get
        )
        assert(!qr.hasNext)
        result
      }
  }

  // balta 0.1.0 binds multi-column NamedParams in the wrong order (https://github.com/AbsaOSS/balta/issues/2),
  // so two-column filters are expressed as SQL conditions; inputs are generated ids and test-constant names only
  private def measureOf(id: Long, name: String): String = s"fk_partitioning = $id AND measure_name = '$name'"

  private def adOf(id: Long, key: String): String = s"fk_partitioning = $id AND ad_name = '$key'"

  private def adValue(id: Long, key: String): Option[Option[String]] = {
    table("runs.additional_data").where(adOf(id, key)) { r =>
      if (r.hasNext) Some(r.next().getString("ad_value")) else None
    }
  }

  private def measureCount(id: Long): Long = table("runs.measure_definitions").count(add("fk_partitioning", id))

  private def measureCount(id: Long, name: String): Long = table("runs.measure_definitions").count(measureOf(id, name))

  private def adCount(id: Long): Long = table("runs.additional_data").count(add("fk_partitioning", id))

  private def adCount(id: Long, key: String): Long = table("runs.additional_data").count(adOf(id, key))

  test("Measure definitions shared by two parents are copied once (union + dedup)") {
    val (p1, p2, child) = createAll()
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addMeasure(p1, "m2", """{"col2"}""", "Alice")
    addMeasure(p2, "m1", """{"col1"}""", "Bob")
    addMeasure(p2, "m3", """{"col3"}""", "Bob")

    val result = copy(child, ids(Seq(p1, p2)), ids(Seq(p1, p2)))

    assert(result == (11, "Parent data copied", Seq.empty[String], 3))
    assert(measureCount(child) == 3)
    assert(measureCount(child, "m1") == 1)
    assert(measureCount(child, "m3") == 1)
    table("runs.measure_definitions").where(measureOf(child, "m2")) { r =>
      assert(r.hasNext)
      assert(r.next().getString("created_by").contains("Alice"))
    }
  }

  test("Measure definition already on the child is not duplicated") {
    val (p1, _, child) = createAll()
    addMeasure(child, "m1", """{"col1"}""", "Carol")
    addMeasure(p1, "m1", """{"col1"}""", "Alice")

    val result = copy(child, ids(Seq(p1)), ids(Seq(p1)))

    assert(result == (11, "Parent data copied", Seq.empty[String], 0))
    assert(measureCount(child, "m1") == 1)
    table("runs.measure_definitions").where(measureOf(child, "m1")) { r =>
      assert(r.hasNext)
      assert(r.next().getString("created_by").contains("Carol"))
    }
  }

  test("Additional data key with the same value on all parents is copied once") {
    val (p1, p2, child) = createAll()
    addAd(p1, "k_same", Some("v"))
    addAd(p2, "k_same", Some("v"))

    val result = copy(child, ids(Seq(p1, p2)), ids(Seq(p1, p2)))

    assert(result == (11, "Parent data copied", Seq.empty[String], 1))
    assert(adCount(child, "k_same") == 1)
    assert(adValue(child, "k_same").contains(Some("v")))
    table("runs.additional_data").where(adOf(child, "k_same")) { r =>
      assert(r.hasNext)
      assert(r.next().getString("created_by").contains("Copier"))
    }
  }

  test("Additional data key with conflicting parent values is skipped and reported") {
    val (p1, p2, child) = createAll()
    addAd(p1, "k_conf", Some("a"))
    addAd(p2, "k_conf", Some("b"))

    val (status, statusText, skipped, copied) = copy(child, ids(Seq(p1, p2)), ids(Seq(p1, p2)))

    assert(status == 11)
    assert(statusText == "Parent data copied")
    assert(adValue(child, "k_conf").isEmpty)
    assert(skipped == Seq("k_conf"))
    assert(copied == 0)
  }

  test("Additional data NULL values: identical NULLs copied once, NULL vs value skipped") {
    val (p1, p2, child) = createAll()
    addAd(p1, "k_null", None)
    addAd(p2, "k_null", None)
    addAd(p1, "k_mix", None)
    addAd(p2, "k_mix", Some("x"))

    val (status, _, skipped, _) = copy(child, ids(Seq(p1, p2)), ids(Seq(p1, p2)))

    assert(status == 11)
    assert(adCount(child, "k_null") == 1)
    assert(adValue(child, "k_null").contains(None))
    assert(adValue(child, "k_mix").isEmpty)
    assert(skipped == Seq("k_mix"))
  }

  test("Child's own additional data always wins and is not reported as skipped") {
    val (p1, p2, child) = createAll()
    addAd(child, "k_own", Some("child"))
    addAd(p1, "k_own", Some("p1"))
    addAd(child, "k_own2", Some("c"))
    addAd(p1, "k_own2", Some("a"))
    addAd(p2, "k_own2", Some("b"))

    val (status, _, skipped, _) = copy(child, ids(Seq(p1, p2)), ids(Seq(p1, p2)))

    assert(status == 11)
    assert(adValue(child, "k_own").contains(Some("child")))
    assert(adValue(child, "k_own2").contains(Some("c")))
    assert(skipped.isEmpty)
  }

  test("Conflict scope parents are checked for conflicts but only copy-from parents are copied") {
    val (p1, p2, child) = createAll()
    addAd(p1, "k_x", Some("a"))
    addAd(p2, "k_x", Some("b"))
    addAd(p2, "k_p2", Some("z"))
    addAd(p1, "k_p1", Some("q"))
    addMeasure(p1, "m_p1", """{"col1"}""", "Alice")
    addMeasure(p2, "m_p2", """{"col2"}""", "Bob")

    val (status, _, skipped, _) = copy(child, ids(Seq(p1, p2)), ids(Seq(p2)))

    assert(status == 11)
    assert(skipped == Seq("k_x"))
    assert(adValue(child, "k_x").isEmpty)
    assert(adValue(child, "k_p2").contains(Some("z")))
    assert(adValue(child, "k_p1").isEmpty)
    assert(measureCount(child, "m_p1") == 0)
    assert(measureCount(child, "m_p2") == 1)
  }

  test("Empty copy-from set copies nothing") {
    val (p1, p2, child) = createAll()
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addAd(p1, "k_a", Some("a"))
    addAd(p2, "k_b", Some("b"))

    val result = copy(child, ids(Seq(p1, p2)), ids(Seq.empty))

    assert(result == (14, "Nothing to copy", Seq.empty[String], 0))
    assert(measureCount(child) == 0)
    assert(adCount(child) == 0)
  }

  test("NULL, duplicate and self ids in copy-from are ignored") {
    val (p1, _, child) = createAll()
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addMeasure(p1, "m2", """{"col2"}""", "Alice")
    addAd(p1, "k_a", Some("a"))
    addAd(p1, "k_n", None)

    val messy = ids(Seq(p1, p1, null, child))
    val result = copy(child, messy, messy)

    assert(result == (11, "Parent data copied", Seq.empty[String], 4))
    assert(measureCount(child) == 2)
    assert(measureCount(child, "m1") == 1)
    assert(measureCount(child, "m2") == 1)
    assert(adCount(child) == 2)
    assert(adValue(child, "k_a").contains(Some("a")))
    assert(adValue(child, "k_n").contains(None))
  }

  test("The earliest parent row wins for a duplicated measure definition, independent of row order") {
    val (p1, p2, child) = createAll()
    // p1's row is inserted first (lower id_measure_definition) but carries the LATER created_at
    addMeasure(p1, "m1", """{"col1"}""", "Alice", Some("2021-01-01T00:00:00Z"))
    addMeasure(p2, "m1", """{"col1"}""", "Bob", Some("2020-01-01T00:00:00Z"))

    val (status, _, _, copied) = copy(child, ids(Seq(p1, p2)), ids(Seq(p1, p2)))

    assert(status == 11)
    assert(copied == 1)
    table("runs.measure_definitions").where(measureOf(child, "m1")) { r =>
      assert(r.hasNext)
      assert(r.next().getString("created_by").contains("Bob"))
      assert(!r.hasNext)
    }
  }

  test("A non-existent or NULL child partitioning returns status 41 and copies nothing") {
    val (p1, _, _) = createAll()
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addAd(p1, "k_a", Some("a"))
    val adBefore = table("runs.additional_data").count()
    val measuresBefore = table("runs.measure_definitions").count()

    val missing = copy(987654321987L, ids(Seq(p1)), ids(Seq(p1)))
    assert(missing == (41, "Partitioning not found", Seq.empty[String], 0))

    function(fncCopyParentsData)
      .setParamNull("i_id_partitioning")
      .setParam("i_conflict_scope_parent_ids", ids(Seq(p1)))
      .setParam("i_copy_from_parent_ids", ids(Seq(p1)))
      .setParam("i_by_user", "Copier")
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Partitioning not found"))
      }

    assert(table("runs.additional_data").count() == adBefore)
    assert(table("runs.measure_definitions").count() == measuresBefore)
  }

  test("EXECUTE on the helper is granted to the owner only") {
    val signature = "runs._copy_parents_data_to_partitioning(bigint,bigint[],bigint[],text)"

    assert(hasExecute("atum_owner", signature))
    assert(!hasExecute("atum_user", signature))
    assert(!hasExecute("atum_reader", signature))
    assert(!hasExecute("public", signature))
  }

  test("The helper pins the search_path") {
    val signature = "runs._copy_parents_data_to_partitioning(bigint,bigint[],bigint[],text)"
    val statement = dbConnection.connection.createStatement()
    try {
      val rs = statement.executeQuery(
        s"SELECT coalesce(proconfig::text, '') LIKE '%search_path=pg_catalog,%' FROM pg_proc WHERE oid = '$signature'::regprocedure"
      )
      assert(rs.next())
      assert(rs.getBoolean(1))
    } finally {
      statement.close()
    }
  }

}

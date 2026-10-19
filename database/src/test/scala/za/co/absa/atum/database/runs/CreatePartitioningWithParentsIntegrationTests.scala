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

class CreatePartitioningWithParentsIntegrationTests extends DBTestSuite {

  private case class CreateResult(status: Int, statusText: String, id: Option[Long], skippedKeys: Seq[String])

  private val fncCreatePartitioning = "runs.create_partitioning"
  private val fncCreateWithParents = "runs.create_partitioning_with_parents"
  private val fncGetAncestors = "runs.get_partitioning_ancestors"

  private val cycleText = "Parent partitioning would create a cycle"

  private val parent1Partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["mpkey1"],
      |  "keysToValuesMap": {
      |    "mpkey1": "valueMP1"
      |  }
      |}
      |""".stripMargin
  )

  private val parent2Partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["mpkey2"],
      |  "keysToValuesMap": {
      |    "mpkey2": "valueMP2"
      |  }
      |}
      |""".stripMargin
  )

  private val parent3Partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["mpkey3"],
      |  "keysToValuesMap": {
      |    "mpkey3": "valueMP3"
      |  }
      |}
      |""".stripMargin
  )

  private val childPartitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["mpkey1", "mpkey2", "mckey"],
      |  "keysToValuesMap": {
      |    "mpkey1": "valueMP1",
      |    "mpkey2": "valueMP2",
      |    "mckey": "valueMC"
      |  }
      |}
      |""".stripMargin
  )

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

  private val missingParentId = 987654321987L

  private def ids(xs: Seq[Any]): CustomDBType =
    CustomDBType(xs.map(x => if (x == null) "NULL" else x.toString).mkString("{", ",", "}"), "BIGINT[]")

  private def createParent(p: JsonBString, author: String): Long = {
    function(fncCreatePartitioning)
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

  private def readResult(qr: QueryResult): CreateResult = {
    assert(qr.hasNext)
    val row = qr.next()
    val result = CreateResult(
      row.getInt("status").get,
      row.getString("status_text").get,
      row.getLong("id_partitioning"),
      row.getArray[String]("skipped_ad_keys").map(_.toSeq).get
    )
    assert(!qr.hasNext)
    result
  }

  private def createMulti(p: JsonBString, parents: CustomDBType): CreateResult = {
    function(fncCreateWithParents)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_parent_partitioning_ids", parents)
      .execute(readResult)
  }

  private def createMultiNull(p: JsonBString): CreateResult = {
    function(fncCreateWithParents)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning_ids")
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

  private def addMeasure(id: Long, name: String, columnsLiteral: String, author: String): Unit = {
    table("runs.measure_definitions").insert(
      add("fk_partitioning", id)
        .add("created_by", author)
        .add("measure_name", name)
        .add("measured_columns", CustomDBType(columnsLiteral, "TEXT[]"))
    )
    ()
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

  private def reparent(child: Long, parent: Long): Unit = {
    function("runs.update_partitioning_parent")
      .setParam("i_id_partitioning", child)
      .setParam("i_id_parent_partitioning", parent)
      .setParam("i_by_user", "Seeder")
      .execute { qr =>
        assert(qr.next().getInt("status").contains(11))
      }
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

  private def hasExecute(role: String, signature: String): Boolean =
    queryBoolean(s"SELECT has_function_privilege('$role', '$signature', 'EXECUTE')")

  // balta 0.1.0 binds multi-column NamedParams in the wrong order (https://github.com/AbsaOSS/balta/issues/2),
  // so two-column filters are expressed as SQL conditions; inputs are generated ids and test-constant names only
  private def measureOf(id: Long, name: String): String = s"fk_partitioning = $id AND measure_name = '$name'"

  private def adOf(id: Long, key: String): String = s"fk_partitioning = $id AND ad_name = '$key'"

  private def measureCount(id: Long): Long = table("runs.measure_definitions").count(add("fk_partitioning", id))

  private def measureCount(id: Long, name: String): Long = table("runs.measure_definitions").count(measureOf(id, name))

  private def adCount(id: Long): Long = table("runs.additional_data").count(add("fk_partitioning", id))

  private def adCount(id: Long, key: String): Long = table("runs.additional_data").count(adOf(id, key))

  private def adValue(id: Long, key: String): Option[Option[String]] = {
    table("runs.additional_data").where(adOf(id, key)) { r =>
      if (r.hasNext) Some(r.next().getString("ad_value")) else None
    }
  }

  // p1 (lower id) becomes a child of p2 (higher id); p2's data is seeded AFTER the re-parenting so p1 does not carry it
  private def reversedTopology(): (Long, Long) = {
    val p1 = createParent(parent1Partitioning, "Alice")
    val p2 = createParent(parent2Partitioning, "Bob")
    assert(p1 < p2)
    reparent(p1, p2)
    addMeasure(p2, "m_p2", """{"col2"}""", "Bob")
    addAd(p2, "k_p2", Some("z"))
    addMeasure(p1, "m_p1", """{"col1"}""", "Alice")
    (p1, p2)
  }

  test("Child created with two parents is linked into both parents' flows in one call") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val p2 = createParent(parent2Partitioning, "Bob")

    val result = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(result.status == 12)
    assert(result.statusText == "Partitioning created with parent partitioning")
    val child = result.id.get
    assert(flowCount(child) == 3)
    assert(flowCount(p1) == 1)
    assert(flowCount(p2) == 1)
    assert(ancestorIds(child) == Set(p1, p2))
  }

  test("Missing parent fails with status 41 and creates nothing") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val ptfBefore = table("flows.partitioning_to_flow").count()

    val result = createMulti(childPartitioning, ids(Seq(p1, missingParentId)))

    assert(result.status == 41)
    assert(result.statusText == "Parent partitioning not found")
    assert(result.id.isEmpty)
    assert(table("runs.partitionings").count(add("partitioning", childPartitioning)) == 0)
    assert(table("flows.partitioning_to_flow").count() == ptfBefore)
  }

  test("Missing parent for an existing child links nothing") {
    val p2 = createParent(parent2Partitioning, "Bob")
    val created = createMulti(childPartitioning, ids(Seq.empty))
    assert(created.status == 11)
    val child = created.id.get
    assert(flowCount(child) == 1)

    val result = createMulti(childPartitioning, ids(Seq(p2, missingParentId)))

    assert(result.status == 41)
    assert(result.statusText == "Parent partitioning not found")
    assert(result.id.isEmpty)
    assert(flowCount(child) == 1)
    assert(flowCount(p2) == 1)
  }

  test("NULL parent array behaves like a parentless create") {
    val result = createMultiNull(childPartitioning)

    assert(result.status == 11)
    assert(result.statusText == "Partitioning created")
    assert(flowCount(result.id.get) == 1)
  }

  test("Empty parent array behaves like a parentless create") {
    val result = createMulti(childPartitioning, CustomDBType("{}", "BIGINT[]"))

    assert(result.status == 11)
    assert(result.statusText == "Partitioning created")
    assert(flowCount(result.id.get) == 1)
  }

  test("Existing partitioning with a NULL or empty parent array is a successful no-op (status 14, not 31)") {
    val child = createParent(childPartitioning, "Carol")

    val withEmpty = createMulti(childPartitioning, ids(Seq.empty))
    assert(withEmpty.status == 14)
    assert(withEmpty.statusText == "Partitioning already present")
    assert(withEmpty.id.contains(child))

    val withNull = createMultiNull(childPartitioning)
    assert(withNull.status == 14)
    assert(withNull.id.contains(child))

    assert(flowCount(child) == 1)
  }

  test("Single-element parent array behaves like the single-parent create and also copies additional data") {
    val p1 = createParent(parent1Partitioning, "Alice")
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addAd(p1, "k1", Some("v1"))

    val result = createMulti(childPartitioning, ids(Seq(p1)))

    assert(result.status == 12)
    assert(result.statusText == "Partitioning created with parent partitioning")
    val child = result.id.get
    assert(flowCount(child) == 2)
    assert(measureCount(child) == 1)
    table("runs.measure_definitions").where(measureOf(child, "m1")) { r =>
      assert(r.hasNext)
      assert(r.next().getString("created_by").contains("Alice"))
    }
    // RD-1 superset: the shipped single-parent create path copies measures only
    assert(adValue(child, "k1").contains(Some("v1")))
  }

  test("Existing child is linked to a newly supplied parent instead of returning status 31") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val child = createParent(childPartitioning, "Carol")
    assert(flowCount(child) == 1)

    val result = createMulti(childPartitioning, ids(Seq(p1)))

    assert(result.status != 31)
    assert(result.status == 13)
    assert(result.statusText == "Partitioning parents registered")
    assert(result.id.contains(child))
    assert(flowCount(child) == 2)
    assert(ancestorIds(child).contains(p1))
  }

  test("Re-submitting the same parents is idempotent") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val p2 = createParent(parent2Partitioning, "Bob")
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addAd(p1, "k1", Some("v1"))

    val first = createMulti(childPartitioning, ids(Seq(p1, p2)))
    assert(first.status == 12)
    val child = first.id.get
    val flowsBefore = flowCount(child)
    val measuresBefore = measureCount(child)
    val adBefore = adCount(child)
    assert(measuresBefore == 1)
    assert(adBefore == 1)

    val second = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(second.status == 14)
    assert(second.statusText == "Partitioning already present")
    assert(second.id.contains(child))
    assert(flowCount(child) == flowsBefore)
    assert(measureCount(child) == measuresBefore)
    assert(adCount(child) == adBefore)
  }

  test("Existing child linked to one more parent copies the new parent's data without duplicating the old") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val p2 = createParent(parent2Partitioning, "Bob")
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addMeasure(p2, "m2", """{"col2"}""", "Bob")

    val first = createMulti(childPartitioning, ids(Seq(p1)))
    assert(first.status == 12)
    val child = first.id.get
    assert(measureCount(child, "m1") == 1)
    assert(measureCount(child, "m2") == 0)

    val second = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(second.status == 13)
    assert(second.statusText == "Partitioning parents registered")
    assert(second.id.contains(child))
    assert(flowCount(child) == 3)
    assert(measureCount(child, "m2") == 1)
    assert(measureCount(child, "m1") == 1)
    assert(measureCount(child) == 2)
  }

  test("Duplicate and NULL parent ids are ignored") {
    val p1 = createParent(parent1Partitioning, "Alice")

    val result = createMulti(childPartitioning, ids(Seq(p1, p1, null)))

    assert(result.status == 12)
    assert(flowCount(result.id.get) == 2)
  }

  test("Two parents sharing a measure and an additional data key give the child a single copy") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val p2 = createParent(parent2Partitioning, "Bob")
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addAd(p1, "k_same", Some("v"))
    addAd(p1, "k_conf", Some("a"))
    addMeasure(p2, "m1", """{"col1"}""", "Bob")
    addMeasure(p2, "m2", """{"col2"}""", "Bob")
    addAd(p2, "k_same", Some("v"))
    addAd(p2, "k_conf", Some("b"))

    val result = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(result.status == 12)
    assert(
      result.statusText == "Partitioning created with parent partitioning; 1 conflicting additional data key(s) skipped"
    )
    assert(result.skippedKeys == Seq("k_conf"))
    val child = result.id.get
    assert(measureCount(child) == 2)
    assert(measureCount(child, "m1") == 1)
    assert(adCount(child, "k_same") == 1)
    assert(adValue(child, "k_same").contains(Some("v")))
    assert(adCount(child, "k_conf") == 0)
    // copied additional data rows are stamped with the user of the call
    table("runs.additional_data").where(adOf(child, "k_same")) { r =>
      assert(r.hasNext)
      assert(r.next().getString("created_by").contains("Fantômas"))
    }
  }

  test("A key skipped for a conflict is not filled in later by another parent (CR-01)") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val p2 = createParent(parent2Partitioning, "Bob")
    val p3 = createParent(parent3Partitioning, "Dave")
    addAd(p1, "k", Some("a"))
    addAd(p3, "k", Some("b"))
    addAd(p2, "k", Some("a"))

    val first = createMulti(childPartitioning, ids(Seq(p1, p3)))
    assert(first.status == 12)
    assert(first.skippedKeys == Seq("k"))
    val child = first.id.get
    assert(adCount(child, "k") == 0)

    val second = createMulti(childPartitioning, ids(Seq(p2)))

    assert(second.status == 13)
    assert(second.statusText == "Partitioning parents registered; 1 conflicting additional data key(s) skipped")
    assert(second.skippedKeys == Seq("k"))
    assert(adCount(child, "k") == 0)
    assert(ancestorIds(child) == Set(p1, p2, p3))
  }

  test(
    "Shipped runs.create_partitioning is untouched: 2-arg, named and positional untyped NULL calls resolve (WR-02)"
  ) {
    val p1 = createParent(parent1Partitioning, "Alice")

    val child = function(fncCreatePartitioning)
      .setParam("i_partitioning", childPartitioning)
      .setParam("i_by_user", "Fantômas")
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    function(fncCreatePartitioning)
      .setParam("i_partitioning", childPartitioning)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_parent_partitioning_id", p1)
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        assert(row.getInt("status").contains(31))
        assert(row.getString("status_text").contains("Partitioning already exists"))
        assert(row.getLong("id_partitioning").contains(child))
      }

    assert(flowCount(child) == 1)

    // positional call with an untyped NULL literal: it was ambiguous with an overload of the shipped function
    val statement = dbConnection.connection.createStatement()
    try {
      val rs = statement.executeQuery(
        """SELECT status, status_text FROM runs.create_partitioning(
          |  '{"version": 1, "keys": ["pn"], "keysToValuesMap": {"pn": "1"}}'::jsonb, 'Fantômas', NULL)""".stripMargin
      )
      assert(rs.next())
      assert(rs.getInt("status") == 11)
      assert(rs.getString("status_text") == "Partitioning created")
      assert(!rs.next())
    } finally {
      statement.close()
    }

    // the shipped function is the only one of that name
    assert(
      queryBoolean(
        "SELECT count(*) = 1 FROM pg_proc WHERE proname = 'create_partitioning' AND pronamespace = 'runs'::regnamespace"
      )
    )
  }

  test("Create copies every parent's data even when the lower-id parent is already a child of the higher-id parent") {
    val (p1, p2) = reversedTopology()

    val result = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(result.status == 12)
    val child = result.id.get
    assert(measureCount(child, "m_p1") == 1)
    assert(measureCount(child, "m_p2") == 1)
    assert(adValue(child, "k_p2").contains(Some("z")))
    assert(flowCount(child) == 3)
  }

  test("Existing child: parents not yet ancestors before the call are all registered and copied") {
    val (p1, p2) = reversedTopology()
    val created = createMulti(childPartitioning, ids(Seq.empty))
    assert(created.status == 11)
    val child = created.id.get

    val result = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(result.status == 13)
    assert(result.statusText == "Partitioning parents registered")
    assert(result.id.contains(child))
    assert(measureCount(child, "m_p1") == 1)
    assert(measureCount(child, "m_p2") == 1)
    assert(adValue(child, "k_p2").contains(Some("z")))

    val flowsBefore = flowCount(child)
    val measuresBefore = measureCount(child)
    val adBefore = adCount(child)

    val again = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(again.status == 14)
    assert(again.statusText == "Partitioning already present")
    assert(again.id.contains(child))
    assert(flowCount(child) == flowsBefore)
    assert(measureCount(child) == measuresBefore)
    assert(adCount(child) == adBefore)
  }

  test("Existing child: a supplied parent that is already an ancestor still has its data copied (WR-01)") {
    val (p1, p2) = reversedTopology()

    val first = createMulti(childPartitioning, ids(Seq(p1)))
    assert(first.status == 12)
    val child = first.id.get
    assert(ancestorIds(child) == Set(p1, p2))
    assert(measureCount(child, "m_p2") == 0)

    val second = createMulti(childPartitioning, ids(Seq(p2)))

    assert(second.status == 13)
    assert(second.id.contains(child))
    assert(measureCount(child, "m_p2") == 1)
    assert(adValue(child, "k_p2").contains(Some("z")))
    assert(ancestorIds(child) == Set(p1, p2))

    val third = createMulti(childPartitioning, ids(Seq(p2)))
    assert(third.status == 14)
    assert(measureCount(child, "m_p2") == 1)
  }

  test("The same parent set gives the same data in one call or in two calls (WR-01)") {
    val (p1, p2) = reversedTopology()
    val otherChild = lineagePartitioning("other-child")

    val oneCall = createMulti(childPartitioning, ids(Seq(p1, p2)))
    val child = oneCall.id.get

    val firstOfTwo = createMulti(otherChild, ids(Seq(p1)))
    assert(firstOfTwo.status == 12)
    val secondOfTwo = createMulti(otherChild, ids(Seq(p1, p2)))
    assert(secondOfTwo.status == 13)
    val other = secondOfTwo.id.get

    assert(measureCount(other) == measureCount(child))
    assert(measureCount(other, "m_p1") == 1)
    assert(measureCount(other, "m_p2") == 1)
    assert(adCount(other) == adCount(child))
    assert(adValue(other, "k_p2") == adValue(child, "k_p2"))
    assert(ancestorIds(other) == ancestorIds(child))
  }

  test("Status 13 is returned when new flow links were written for an already registered parent (WR-06)") {
    val g = createParent(parent3Partitioning, "Dave")
    val p2 = createParent(parent2Partitioning, "Bob")
    val first = createMulti(childPartitioning, ids(Seq(p2)))
    assert(first.status == 12)
    val child = first.id.get
    assert(ancestorIds(child) == Set(p2))

    // p2 gains a flow after the child was linked to it
    reparent(p2, g)
    assert(ancestorIds(child) == Set(p2))

    val second = createMulti(childPartitioning, ids(Seq(p2)))

    assert(second.status == 13)
    assert(second.statusText == "Partitioning parents registered")
    assert(ancestorIds(child) == Set(p2, g))

    val third = createMulti(childPartitioning, ids(Seq(p2)))
    assert(third.status == 14)
  }

  test("A partitioning cannot be linked as its own parent (CR-02)") {
    val child = createParent(childPartitioning, "Carol")
    val ptfBefore = table("flows.partitioning_to_flow").count()

    val result = createMulti(childPartitioning, ids(Seq(child)))

    assert(result.status == 43)
    assert(result.statusText == cycleText)
    assert(result.id.isEmpty)
    assert(table("flows.partitioning_to_flow").count() == ptfBefore)
    assert(ancestorIds(child).isEmpty)
  }

  test("A descendant cannot be linked as a parent of its ancestor and nothing is written (CR-02)") {
    val c = createParent(lineagePartitioning("C"), "Carol")
    val created = createMulti(lineagePartitioning("D"), ids(Seq(c)))
    assert(created.status == 12)
    val d = created.id.get
    addMeasure(d, "m_d", """{"col"}""", "Dave")
    addAd(d, "k_d", Some("x"))
    val ptfBefore = table("flows.partitioning_to_flow").count()

    val result = createMulti(lineagePartitioning("C"), ids(Seq(d)))

    assert(result.status == 43)
    assert(result.statusText == cycleText)
    assert(result.id.isEmpty)
    assert(table("flows.partitioning_to_flow").count() == ptfBefore)
    assert(ancestorIds(c).isEmpty)
    assert(ancestorIds(d) == Set(c))
    assert(measureCount(c, "m_d") == 0)
    assert(adCount(c, "k_d") == 0)
  }

  test("A transitive descendant among the parents rejects the whole call, valid parents included (CR-02)") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val c = createParent(lineagePartitioning("C"), "Carol")
    val d = createMulti(lineagePartitioning("D"), ids(Seq(c))).id.get
    val e = createMulti(lineagePartitioning("E"), ids(Seq(d))).id.get
    assert(ancestorIds(e) == Set(c, d))
    val ptfBefore = table("flows.partitioning_to_flow").count()

    val result = createMulti(lineagePartitioning("C"), ids(Seq(p1, e)))

    assert(result.status == 43)
    assert(result.statusText == cycleText)
    assert(table("flows.partitioning_to_flow").count() == ptfBefore)
    assert(ancestorIds(c).isEmpty)
  }

  test("Descendants of an existing child become reachable from a newly linked parent (WR-05)") {
    val n = createParent(parent1Partitioning, "Alice")
    val c = createParent(lineagePartitioning("C"), "Carol")
    val d = createMulti(lineagePartitioning("D"), ids(Seq(c))).id.get
    val e = createMulti(lineagePartitioning("E"), ids(Seq(d))).id.get
    assert(ancestorIds(d) == Set(c))
    assert(ancestorIds(e) == Set(c, d))

    val result = createMulti(lineagePartitioning("C"), ids(Seq(n)))

    assert(result.status == 13)
    assert(ancestorIds(c) == Set(n))
    assert(ancestorIds(d) == Set(c, n))
    assert(ancestorIds(e) == Set(c, d, n))

    val flowsBefore = table("flows.partitioning_to_flow").count()
    val again = createMulti(lineagePartitioning("C"), ids(Seq(n)))
    assert(again.status == 14)
    assert(table("flows.partitioning_to_flow").count() == flowsBefore)
  }

  test("Privileges: execute is granted to the owner and atum_user only, and the search_path is pinned (WR-04, WR-08)") {
    val signature = "runs.create_partitioning_with_parents(jsonb,text,bigint[])"

    assert(hasExecute("atum_owner", signature))
    assert(hasExecute("atum_user", signature))
    assert(!hasExecute("atum_reader", signature))
    assert(!hasExecute("public", signature))
    assert(
      queryBoolean(
        s"SELECT coalesce(proconfig::text, '') LIKE '%search_path=pg_catalog,%' FROM pg_proc WHERE oid = '$signature'::regprocedure"
      )
    )
  }

}

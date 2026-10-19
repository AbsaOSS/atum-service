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

class CreatePartitioningMultiParentIntegrationTests extends DBTestSuite {

  private val fncCreatePartitioning = "runs.create_partitioning"
  private val fncGetAncestors = "runs.get_partitioning_ancestors"

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

  private val missingParentId = 987654321987L

  private def ids(xs: Seq[Any]): CustomDBType =
    CustomDBType(xs.map(x => if (x == null) "NULL" else x.toString).mkString("{", ",", "}"), "BIGINT[]")

  private def createParent(p: JsonBString, author: String): Long = {
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

  private def createMulti(p: JsonBString, parents: CustomDBType): (Int, String, Option[Long]) = {
    function(fncCreatePartitioning)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", "Fantômas")
      .setParam("i_parent_partitioning_ids", parents)
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        val r = (row.getInt("status").get, row.getString("status_text").get, row.getLong("id_partitioning"))
        assert(!qr.hasNext)
        r
      }
  }

  private def createMultiNull(p: JsonBString): (Int, String, Option[Long]) = {
    function(fncCreatePartitioning)
      .setParam("i_partitioning", p)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning_ids")
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        val r = (row.getInt("status").get, row.getString("status_text").get, row.getLong("id_partitioning"))
        assert(!qr.hasNext)
        r
      }
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

    val (status, statusText, idOpt) = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(status == 12)
    assert(statusText == "Partitioning created with parent partitioning")
    val child = idOpt.get
    assert(flowCount(child) == 3)
    assert(flowCount(p1) == 1)
    assert(flowCount(p2) == 1)
    assert(ancestorIds(child) == Set(p1, p2))
  }

  test("Missing parent fails with status 41 and creates nothing") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val ptfBefore = table("flows.partitioning_to_flow").count()

    val (status, statusText, idOpt) = createMulti(childPartitioning, ids(Seq(p1, missingParentId)))

    assert(status == 41)
    assert(statusText == "Parent partitioning not found")
    assert(idOpt.isEmpty)
    assert(table("runs.partitionings").count(add("partitioning", childPartitioning)) == 0)
    assert(table("flows.partitioning_to_flow").count() == ptfBefore)
  }

  test("Missing parent for an existing child links nothing") {
    val p2 = createParent(parent2Partitioning, "Bob")
    val (createStatus, _, childOpt) = createMulti(childPartitioning, ids(Seq.empty))
    assert(createStatus == 11)
    val child = childOpt.get
    assert(flowCount(child) == 1)

    val (status, statusText, idOpt) = createMulti(childPartitioning, ids(Seq(p2, missingParentId)))

    assert(status == 41)
    assert(statusText == "Parent partitioning not found")
    assert(idOpt.isEmpty)
    assert(flowCount(child) == 1)
    assert(flowCount(p2) == 1)
  }

  test("NULL parent array behaves like a parentless create") {
    val (status, statusText, idOpt) = createMultiNull(childPartitioning)

    assert(status == 11)
    assert(statusText == "Partitioning created")
    assert(flowCount(idOpt.get) == 1)
  }

  test("Empty parent array behaves like a parentless create") {
    val (status, statusText, idOpt) = createMulti(childPartitioning, CustomDBType("{}", "BIGINT[]"))

    assert(status == 11)
    assert(statusText == "Partitioning created")
    assert(flowCount(idOpt.get) == 1)
  }

  test("Single-element parent array behaves like the single-parent create and also copies additional data") {
    val p1 = createParent(parent1Partitioning, "Alice")
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addAd(p1, "k1", Some("v1"))

    val (status, statusText, idOpt) = createMulti(childPartitioning, ids(Seq(p1)))

    assert(status == 12)
    assert(statusText == "Partitioning created with parent partitioning")
    val child = idOpt.get
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

    val (status, statusText, idOpt) = createMulti(childPartitioning, ids(Seq(p1)))

    assert(status != 31)
    assert(status == 13)
    assert(statusText == "Partitioning parents registered")
    assert(idOpt.contains(child))
    assert(flowCount(child) == 2)
    assert(ancestorIds(child).contains(p1))
  }

  test("Re-submitting the same parents is idempotent") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val p2 = createParent(parent2Partitioning, "Bob")
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addAd(p1, "k1", Some("v1"))

    val (status1, _, idOpt1) = createMulti(childPartitioning, ids(Seq(p1, p2)))
    assert(status1 == 12)
    val child = idOpt1.get
    val flowsBefore = flowCount(child)
    val measuresBefore = measureCount(child)
    val adBefore = adCount(child)
    assert(measuresBefore == 1)
    assert(adBefore == 1)

    val (status2, statusText2, idOpt2) = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(status2 == 14)
    assert(statusText2 == "Partitioning already present")
    assert(idOpt2.contains(child))
    assert(flowCount(child) == flowsBefore)
    assert(measureCount(child) == measuresBefore)
    assert(adCount(child) == adBefore)
  }

  test("Existing child linked to one more parent copies only the new parent's data") {
    val p1 = createParent(parent1Partitioning, "Alice")
    val p2 = createParent(parent2Partitioning, "Bob")
    addMeasure(p1, "m1", """{"col1"}""", "Alice")
    addMeasure(p2, "m2", """{"col2"}""", "Bob")

    val (status1, _, idOpt1) = createMulti(childPartitioning, ids(Seq(p1)))
    assert(status1 == 12)
    val child = idOpt1.get
    assert(measureCount(child, "m1") == 1)
    assert(measureCount(child, "m2") == 0)

    val (status2, statusText2, idOpt2) = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(status2 == 13)
    assert(statusText2 == "Partitioning parents registered")
    assert(idOpt2.contains(child))
    assert(flowCount(child) == 3)
    assert(measureCount(child, "m2") == 1)
    assert(measureCount(child, "m1") == 1)
    assert(measureCount(child) == 2)
  }

  test("Duplicate and NULL parent ids are ignored") {
    val p1 = createParent(parent1Partitioning, "Alice")

    val (status, _, idOpt) = createMulti(childPartitioning, ids(Seq(p1, p1, null)))

    assert(status == 12)
    assert(flowCount(idOpt.get) == 2)
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

    val (status, statusText, idOpt) = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(status == 12)
    assert(statusText == "Partitioning created with parent partitioning; conflicting additional data skipped: k_conf")
    val child = idOpt.get
    assert(measureCount(child) == 2)
    assert(measureCount(child, "m1") == 1)
    assert(adCount(child, "k_same") == 1)
    assert(adValue(child, "k_same").contains(Some("v")))
    assert(adCount(child, "k_conf") == 0)
  }

  test("Shipped single-parent overload still resolves for 2-arg and named single-parent calls") {
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
  }

  test("Create copies every parent's data even when the lower-id parent is already a child of the higher-id parent") {
    val (p1, p2) = reversedTopology()

    val (status, _, idOpt) = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(status == 12)
    val child = idOpt.get
    assert(measureCount(child, "m_p1") == 1)
    assert(measureCount(child, "m_p2") == 1)
    assert(adValue(child, "k_p2").contains(Some("z")))
    assert(flowCount(child) == 3)
  }

  test("Existing child: parents not yet ancestors before the call are all registered and copied") {
    val (p1, p2) = reversedTopology()
    val (createStatus, _, childOpt) = createMulti(childPartitioning, ids(Seq.empty))
    assert(createStatus == 11)
    val child = childOpt.get

    val (status, statusText, idOpt) = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(status == 13)
    assert(statusText == "Partitioning parents registered")
    assert(idOpt.contains(child))
    assert(measureCount(child, "m_p1") == 1)
    assert(measureCount(child, "m_p2") == 1)
    assert(adValue(child, "k_p2").contains(Some("z")))

    val flowsBefore = flowCount(child)
    val measuresBefore = measureCount(child)
    val adBefore = adCount(child)

    val (status2, statusText2, idOpt2) = createMulti(childPartitioning, ids(Seq(p1, p2)))

    assert(status2 == 14)
    assert(statusText2 == "Partitioning already present")
    assert(idOpt2.contains(child))
    assert(flowCount(child) == flowsBefore)
    assert(measureCount(child) == measuresBefore)
    assert(adCount(child) == adBefore)
  }

  test("Existing child: a supplied parent that is already an ancestor is not re-copied") {
    val (p1, p2) = reversedTopology()

    val (status1, _, idOpt1) = createMulti(childPartitioning, ids(Seq(p1)))
    assert(status1 == 12)
    val child = idOpt1.get
    assert(ancestorIds(child) == Set(p1, p2))
    assert(measureCount(child, "m_p2") == 0)

    val (status2, statusText2, idOpt2) = createMulti(childPartitioning, ids(Seq(p2)))

    assert(status2 == 14)
    assert(statusText2 == "Partitioning already present")
    assert(idOpt2.contains(child))
    assert(measureCount(child, "m_p2") == 0)
    assert(adValue(child, "k_p2").isEmpty)
    assert(ancestorIds(child) == Set(p1, p2))
  }

}

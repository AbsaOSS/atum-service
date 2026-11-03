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

package za.co.absa.atum.database.flows

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString
import za.co.absa.balta.classes.setter.CustomDBType

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Random

/**
 *  Helper-level tests of `flows._link_to_parents` (LINEAGE-03..06).
 *
 *  Test-design pitfall: the advisory lock is transaction-scoped and `DBTestSuite` runs the whole test body in a
 *  single transaction (rolled back at the end). Once ANY slow-path call has happened in a test, the lock count of
 *  the session stays 1 until the rollback. Therefore:
 *   - every fixture graph is seeded WITHOUT the helper (`runs.create_partitioning` of the v1 path takes no lock),
 *   - all `advisoryLocksHeld() == 0` assertions precede the first slow-path `_link_to_parents` call of a test,
 *   - the slow-lock test asserts `== 1` as its very last step.
 */
class LinkToParentsIntegrationTests extends DBTestSuite {

  private val fncLink = "flows._link_to_parents"
  private val fncCreatePartitioning = "runs.create_partitioning"
  private val fncGetAncestors = "runs.get_partitioning_ancestors"
  private val fncGetFlowPartitionings = "flows.get_flow_partitionings"
  private val fncGetFlowCheckpoints = "flows.get_flow_checkpoints"
  private val helperSignature = "flows._link_to_parents(bigint,bigint[],text)"
  private val cycleText = "Parent partitioning would create a cycle"

  private def partitioningOf(name: String): JsonBString = JsonBString(
    s"""{"version":1,"keys":["lk"],"keysToValuesMap":{"lk":"$name"}}"""
  )

  private def uniq(prefix: String): String = s"$prefix-${UUID.randomUUID()}"

  private def ids(xs: Seq[Any]): CustomDBType =
    CustomDBType(xs.map(x => if (x == null) "NULL" else x.toString).mkString("{", ",", "}"), "BIGINT[]")

  /** Seeds a partitioning via the v1 path (takes NO advisory lock); an optional parent is linked with it. */
  private def seed(name: String, parent: Option[Long] = None): Long = {
    val call = function(fncCreatePartitioning)
      .setParam("i_partitioning", partitioningOf(name))
      .setParam("i_by_user", "Seeder")
    val withParent = parent match {
      case Some(p) => call.setParam("i_parent_partitioning_id", p)
      case None => call.setParamNull("i_parent_partitioning_id")
    }
    withParent.execute { qr =>
      assert(qr.hasNext)
      val row = qr.next()
      assert(row.getInt("status").exists(s => s == 11 || s == 12))
      row.getLong("id_partitioning").get
    }
  }

  private def link(child: Long, parents: CustomDBType): (Int, String) = {
    function(fncLink)
      .setParam("i_child_id", child)
      .setParam("i_parent_ids", parents)
      .setParam("i_by_user", "Linker")
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        val result = (row.getInt("status").get, row.getString("status_text").get)
        assert(!qr.hasNext)
        result
      }
  }

  private def linkNull(child: Long): (Int, String) = {
    function(fncLink)
      .setParam("i_child_id", child)
      .setParamNull("i_parent_ids")
      .setParam("i_by_user", "Linker")
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        (row.getInt("status").get, row.getString("status_text").get)
      }
  }

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

  private def flowOf(partitioningId: Long): Long = {
    table("flows.flows").where(s"fk_primary_partitioning = $partitioningId") { qr =>
      assert(qr.hasNext)
      qr.next().getLong("id_flow").get
    }
  }

  private def flowMembers(partitioningId: Long): Set[Long] = {
    function(fncGetFlowPartitionings)
      .setParam("i_flow_id", flowOf(partitioningId))
      .setParamNull("i_limit")
      .execute { qr =>
        Iterator
          .continually(qr)
          .takeWhile(_.hasNext)
          .flatMap(_.next().getLong("id"))
          .toSet
      }
  }

  private def flowCheckpointIds(partitioningId: Long): Set[UUID] = {
    function(fncGetFlowCheckpoints)
      .setParam("i_flow_id", flowOf(partitioningId))
      .setParamNull("i_checkpoints_limit")
      .execute { qr =>
        Iterator
          .continually(qr)
          .takeWhile(_.hasNext)
          .flatMap(_.next().getUUID("id_checkpoint"))
          .toSet
      }
  }

  /** Inserts one checkpoint with one measurement on the partitioning (flow checkpoints only list measured ones). */
  private def seedCheckpoint(partitioningId: Long): UUID = {
    val checkpointId = UUID.randomUUID()
    val definitionId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", definitionId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "cnt")
        .add("measured_columns", CustomDBType("""{"col1"}""", "TEXT[]"))
        .add("created_by", "Seeder")
    )
    table("runs.checkpoints").insert(
      add("id_checkpoint", checkpointId)
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "cp")
        .add("measured_by_atum_agent", true)
        .add("process_start_time", OffsetDateTime.parse("2024-04-24T10:00:00Z"))
        .add("process_end_time", OffsetDateTime.parse("2024-04-24T10:05:00Z"))
        .add("created_by", "Seeder")
    )
    table("runs.measurements").insert(
      add("fk_measure_definition", definitionId)
        .add("fk_checkpoint", checkpointId)
        .add("measurement_value", JsonBString("""{"measure":{"measureName":"cnt","measuredColumns":["col1"]},""" +
          """"result":{"value":"3","type":"int"}}"""))
    )
    checkpointId
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

  /** Advisory locks held by THIS session (the transaction-scoped lock is visible in pg_locks for our own backend). */
  private def advisoryLocksHeld(): Long = queryLong(
    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND pid = pg_backend_pid()"
  )

  private def hasExecute(role: String): Boolean =
    queryBoolean(s"SELECT has_function_privilege('$role', '$helperSignature', 'EXECUTE')")

  private def counts(): Seq[Long] = Seq(
    table("runs.partitionings").count(),
    table("flows.partitioning_to_flow").count(),
    table("runs.measure_definitions").count(),
    table("runs.additional_data").count()
  )

  test("fast path: re-linking direct and transitive ancestors returns 14, writes nothing and takes no lock") {
    val a = seed(uniq("a"))
    val b = seed(uniq("b"), Some(a))
    val c = seed(uniq("c"), Some(b))
    val before = counts()

    val (status, _) = link(c, ids(Seq(a)))
    assert(status == 14)
    assert(link(c, ids(Seq(b)))._1 == 14)
    assert(link(c, ids(Seq(a, b)))._1 == 14)
    assert(link(b, ids(Seq(a)))._1 == 14)

    assert(counts() == before)
    assert(advisoryLocksHeld() == 0)
  }

  test("empty and NULL parent arrays, NULL elements and duplicates are ignored (14, no lock)") {
    val a = seed(uniq("a"))
    val c = seed(uniq("c"), Some(a))
    val before = counts()

    assert(link(c, ids(Seq.empty))._1 == 14)
    assert(linkNull(c)._1 == 14)
    assert(link(c, ids(Seq(null)))._1 == 14)
    assert(link(c, ids(Seq(null, a, a)))._1 == 14)

    assert(counts() == before)
    assert(advisoryLocksHeld() == 0)
  }

  test("self-link returns 32 naming the id, writes nothing and never takes the fast path or the lock") {
    val x = seed(uniq("x"))
    val before = counts()

    val (status, text) = link(x, ids(Seq(x)))

    assert(status == 32)
    assert(text.contains(cycleText))
    assert(text.contains(x.toString))
    assert(counts() == before)
    assert(advisoryLocksHeld() == 0)
  }

  test("direct cycle (parent is a child of the child) returns 32 and writes nothing") {
    val a = seed(uniq("a"))
    val b = seed(uniq("b"), Some(a))
    val before = counts()

    val (status, text) = link(a, ids(Seq(b)))

    assert(status == 32)
    assert(text.contains(cycleText))
    assert(text.contains(b.toString))
    assert(counts() == before)
    assert(ancestorIds(a).isEmpty)
  }

  test("indirect cycle (parent is a grandchild of the child) returns 32 naming it and writes nothing") {
    val a = seed(uniq("a"))
    val b = seed(uniq("b"), Some(a))
    val c = seed(uniq("c"), Some(b))
    val before = counts()

    val (status, text) = link(a, ids(Seq(c)))

    assert(status == 32)
    assert(text.endsWith(c.toString))
    assert(counts() == before)
  }

  test("mixed parents (one fine, one cycle-forming) return 32 and nothing is written for the fine one either") {
    val a = seed(uniq("a"))
    val b = seed(uniq("b"), Some(a))
    val c = seed(uniq("c"), Some(b))
    val n = seed(uniq("n"))
    val before = counts()

    val (status, text) = link(a, ids(Seq(n, c)))

    assert(status == 32)
    assert(text.endsWith(c.toString))
    assert(!text.contains(n.toString))
    assert(counts() == before)
    assert(ancestorIds(a).isEmpty)
  }

  test("several cycle-forming parents are reported comma separated in ascending order") {
    val a = seed(uniq("a"))
    val b = seed(uniq("b"), Some(a))
    val c = seed(uniq("c"), Some(b))
    val before = counts()

    val (status, text) = link(a, ids(Seq(c, b)))

    assert(status == 32)
    assert(text.endsWith(Seq(b, c).sorted.mkString(",")))
    assert(counts() == before)
  }

  test("chain: linking B under A and C under B gives complete ancestors and flow members") {
    val a = seed(uniq("a"))
    val b = seed(uniq("b"))
    val c = seed(uniq("c"))

    assert(link(b, ids(Seq(a)))._1 == 11)
    assert(link(c, ids(Seq(b)))._1 == 11)

    assert(ancestorIds(c) == Set(a, b))
    assert(ancestorIds(b) == Set(a))
    assert(flowMembers(a) == Set(a, b, c))
    assert(flowMembers(b) == Set(b, c))
    assert(flowMembers(c) == Set(c))
  }

  test("diamond in one call: D under B and E (both children of A) has ancestors A, B and E") {
    val a = seed(uniq("a"))
    val b = seed(uniq("b"), Some(a))
    val e = seed(uniq("e"), Some(a))
    val d = seed(uniq("d"))

    assert(link(d, ids(Seq(b, e)))._1 == 11)

    assert(ancestorIds(d) == Set(a, b, e))
    assert(flowMembers(a) == Set(a, b, e, d))
    assert(flowMembers(b) == Set(b, d))
    assert(flowMembers(e) == Set(e, d))
    assert(table("flows.partitioning_to_flow").count(add("fk_partitioning", d)) == 4)
  }

  test("diamond in two calls: no duplicate key error and the same ancestors") {
    val a = seed(uniq("a"))
    val b = seed(uniq("b"), Some(a))
    val e = seed(uniq("e"), Some(a))
    val d = seed(uniq("d"))

    assert(link(d, ids(Seq(b)))._1 == 11)
    assert(link(d, ids(Seq(e)))._1 == 11)
    assert(link(d, ids(Seq(a, b, e)))._1 == 14)

    assert(ancestorIds(d) == Set(a, b, e))
    assert(table("flows.partitioning_to_flow").count(add("fk_partitioning", d)) == 4)
  }

  test("descendants of the child are propagated into the new parent's flows") {
    val c = seed(uniq("c"))
    val d = seed(uniq("d"), Some(c))
    val n = seed(uniq("n"))

    assert(link(c, ids(Seq(n)))._1 == 11)

    assert(ancestorIds(d) == Set(c, n))
    assert(ancestorIds(c) == Set(n))
    assert(flowMembers(n) == Set(n, c, d))
    assert(flowMembers(c) == Set(c, d))
  }

  /** Roles: X <- Y <- Z is a chain, N a new top-level partitioning; the FINAL graph is N <- X <- Y <- Z. */
  private def orderIndependenceScenario(chainFirst: Boolean): Map[String, Set[String]] = {
    val x = seed(uniq("x"))
    val y = seed(uniq("y"))
    val z = seed(uniq("z"))
    val n = seed(uniq("n"))
    val checkpoint = seedCheckpoint(z)
    val roleOf = Map(x -> "X", y -> "Y", z -> "Z", n -> "N")
    def roles(s: Set[Long]): Set[String] = s.map(roleOf)

    val steps: Seq[() => (Int, String)] =
      if (chainFirst) {
        Seq(() => link(y, ids(Seq(x))), () => link(z, ids(Seq(y))), () => link(x, ids(Seq(n))))
      } else {
        Seq(() => link(x, ids(Seq(n))), () => link(y, ids(Seq(x))), () => link(z, ids(Seq(y))))
      }
    steps.foreach(step => assert(step()._1 == 11))

    // flow checkpoints seen through every flow above the leaf hold exactly the leaf's checkpoint
    Seq(n, x, y).foreach(p => assert(flowCheckpointIds(p) == Set(checkpoint)))

    Map(
      "ancestors(Z)" -> roles(ancestorIds(z)),
      "ancestors(Y)" -> roles(ancestorIds(y)),
      "ancestors(X)" -> roles(ancestorIds(x)),
      "ancestors(N)" -> roles(ancestorIds(n)),
      "members(F(N))" -> roles(flowMembers(n)),
      "members(F(X))" -> roles(flowMembers(x)),
      "members(F(Y))" -> roles(flowMembers(y)),
      "members(F(Z))" -> roles(flowMembers(z))
    )
  }

  private val expectedFinalGraph: Map[String, Set[String]] = Map(
    "ancestors(Z)" -> Set("Y", "X", "N"),
    "ancestors(Y)" -> Set("X", "N"),
    "ancestors(X)" -> Set("N"),
    "ancestors(N)" -> Set.empty[String],
    "members(F(N))" -> Set("N", "X", "Y", "Z"),
    "members(F(X))" -> Set("X", "Y", "Z"),
    "members(F(Y))" -> Set("Y", "Z"),
    "members(F(Z))" -> Set("Z")
  )

  test("order independence: chain first (Y<-X, Z<-Y, then X<-N) gives the full closure") {
    assert(orderIndependenceScenario(chainFirst = true) == expectedFinalGraph)
  }

  test("order independence: N first (X<-N, then Y<-X, Z<-Y) gives the identical closure") {
    assert(orderIndependenceScenario(chainFirst = false) == expectedFinalGraph)
  }

  test("privileges: only atum_owner may execute the helper and its search_path is pinned") {
    assert(hasExecute("atum_owner"))
    assert(!hasExecute("atum_user"))
    assert(!hasExecute("atum_reader"))
    assert(!hasExecute("public"))
    assert(
      queryBoolean(
        s"SELECT 'search_path=pg_catalog, public, pg_temp' = ANY(proconfig) " +
          s"FROM pg_proc WHERE oid = '$helperSignature'::regprocedure"
      )
    )
  }

  test("slow path: a first-time link takes exactly one global advisory lock") {
    val n = seed(uniq("n"))
    val x = seed(uniq("x"))
    assert(advisoryLocksHeld() == 0)

    val (status, _) = link(x, ids(Seq(n)))

    assert(status == 11)
    assert(ancestorIds(x) == Set(n))
    assert(advisoryLocksHeld() == 1)
  }
}

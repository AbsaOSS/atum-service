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

import java.util.UUID

/**
 * Integration tests for the explicit lineage edges feature.
 *
 * Tests verify:
 * 1. Edges are written alongside flows when linking partitionings
 * 2. Direct parent queries return correct results
 * 3. Idempotent linking doesn't duplicate edges
 * 4. Cycle detection still works with edges table
 * 5. Enrichment of ancestor/descendant queries with relationship types
 */
class PartitioningEdgesIntegrationTests extends DBTestSuite {

  private val fncLink = "flows._link_to_parents"
  private val fncCreatePartitioning = "runs.create_partitioning"
  private val fncGetDirectParents = "flows.get_direct_parents"

  private def partitioningJson(name: String): String =
    s"""{"version":1,"keys":["lk"],"keysToValuesMap":{"lk":"$name"}}"""

  private def partitioningOf(name: String): JsonBString = JsonBString(partitioningJson(name))

  private def uniq(prefix: String): String = s"$prefix-${UUID.randomUUID()}"

  private def ids(xs: Seq[Any]): CustomDBType =
    CustomDBType(xs.map(x => if (x == null) "NULL" else x.toString).mkString("{", ",", "}"), "BIGINT[]")

  /** Seeds a partitioning via the v1 path (no edges written in v1 path) */
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
        (row.getInt("status").get, row.getString("status_text").get)
      }
  }

  private def countEdges(child: Long): Int = {
    table("flows.partitioning_edges")
      .count(add("fk_child_partitioning", child)).toInt
  }

  private def countFlowMemberships(partitioningId: Long): Int = {
    table("flows.partitioning_to_flow")
      .count(add("fk_partitioning", partitioningId)).toInt
  }

  private def getDirectParents(childId: Long): Seq[(Long, String)] = {
    function(fncGetDirectParents)
      .setParam("i_partitioning_id", childId)
      .execute { qr =>
        val results = scala.collection.mutable.ArrayBuffer[(Long, String)]()
        while (qr.hasNext) {
          val row = qr.next()
          val status = row.getInt("status").get
          if (status == 11) {
            row.getLong("parent_id").foreach { parentId =>
              results += ((parentId, row.getString("parent_author").getOrElse("unknown")))
            }
          }
        }
        results.toSeq
      }
  }

  test("create partitioning with single parent writes to both edges and flows tables") {
    val a = seed(uniq("A"))
    val b = seed(uniq("B"))

    // Link B under A
    val (status, _) = link(b, ids(Seq(a)))
    assert(status == 11, "linking should succeed")

    // Verify edge written
    val edges = countEdges(b)
    assert(edges == 1, s"should have 1 edge, got $edges")

    // Verify flow memberships written (B should be in A's flow + its own flow)
    val memberships = countFlowMemberships(b)
    assert(memberships >= 2, s"should have at least 2 flow memberships, got $memberships")

    // Verify get_direct_parents returns A
    val parents = getDirectParents(b)
    assert(parents.length == 1, s"should have 1 direct parent, got ${parents.length}")
    assert(parents.head._1 == a, s"parent should be $a, got ${parents.head._1}")
  }

  test("create partitioning with multiple parents writes all edges") {
    val a = seed(uniq("A"))
    val b = seed(uniq("B"))
    val c = seed(uniq("C"))

    // Link C under both A and B (diamond structure)
    val (status, _) = link(c, ids(Seq(a, b)))
    assert(status == 11, "linking should succeed")

    // Verify 2 edges written
    val edges = countEdges(c)
    assert(edges == 2, s"should have 2 edges, got $edges")

    // Verify get_direct_parents returns both A and B
    val parents = getDirectParents(c).sortBy(_._1)
    assert(parents.length == 2, s"should have 2 direct parents, got ${parents.length}")
    assert(parents(0)._1 == a, s"first parent should be $a")
    assert(parents(1)._1 == b, s"second parent should be $b")
  }

  test("idempotent linking does not duplicate edges") {
    val a = seed(uniq("A"))
    val b = seed(uniq("B"))

    // Link B under A twice
    link(b, ids(Seq(a)))
    val (status2, _) = link(b, ids(Seq(a)))
    assert(status2 == 14, "second link should return status 14 (nothing changed)")

    // Verify still only 1 edge
    val edges = countEdges(b)
    assert(edges == 1, s"should still have 1 edge after duplicate link, got $edges")
  }

  test("cycle detection rejects invalid edges") {
    val a = seed(uniq("A"))
    val b = seed(uniq("B"))

    // Link B under A (A → B)
    link(b, ids(Seq(a)))

    // Try to link A under B (B → A) - should reject cycle
    val (status, text) = link(a, ids(Seq(b)))
    assert(status == 32, s"should reject cycle with status 32, got $status")
    assert(text.contains("cycle"), s"status text should mention cycle, got: $text")

    // Verify no edge from B to A
    val edgesAtoB = table("flows.partitioning_edges")
      .count(s"fk_child_partitioning = $a AND fk_parent_partitioning = $b")
    assert(edgesAtoB == 0, "should have no edge from B to A")
  }

  test("get_direct_parents returns empty for partitioning without parents") {
    val a = seed(uniq("A"))

    val parents = getDirectParents(a)
    assert(parents.isEmpty, "root partitioning should have no parents")
  }

  test("get_direct_parents returns 404 for non-existent partitioning") {
    val nonExistentId = 999999999L

    function(fncGetDirectParents)
      .setParam("i_partitioning_id", nonExistentId)
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        val status = row.getInt("status").get
        assert(status == 41, s"should return status 41 (not found), got $status")
      }
  }

  test("linking leaf under new parent writes edges for complex lineage") {
    // A → B → C
    val a = seed(uniq("A"))
    val b = seed(uniq("B"))
    link(b, ids(Seq(a)))
    val c = seed(uniq("C"))
    link(c, ids(Seq(b)))

    // Verify edges
    assert(countEdges(b) == 1, "B should have 1 edge to A")
    assert(countEdges(c) == 1, "C should have 1 edge to B")

    // Verify direct parents
    val parentsB = getDirectParents(b)
    assert(parentsB.length == 1 && parentsB.head._1 == a, "B's parent should be A")

    val parentsC = getDirectParents(c)
    assert(parentsC.length == 1 && parentsC.head._1 == b, "C's parent should be B")
  }

  test("linking leaf directly to an existing transitive ancestor records the edge") {
    // A → B → C
    val a = seed(uniq("A"))
    val b = seed(uniq("B"))
    link(b, ids(Seq(a)))
    val c = seed(uniq("C"))
    link(c, ids(Seq(b)))

    // Now link C directly to A as well (C is already in A's flow via B)
    val (status, _) = link(c, ids(Seq(a)))
    assert(status == 11, "linking C directly to transitive ancestor A should succeed")

    // Verify C now has 2 direct edges (to B and to A)
    val edges = countEdges(c)
    assert(edges == 2, s"C should have 2 edges, got $edges")

    val parentsC = getDirectParents(c).sortBy(_._1)
    assert(parentsC.length == 2, s"C should have 2 direct parents, got ${parentsC.length}")
    val expectedParents = Seq(a, b).sorted
    assert(parentsC.map(_._1) == expectedParents, s"C's parents should be $expectedParents")
  }

  test("self-loop constraint prevents A→A edges") {
    val a = seed(uniq("A"))

    // Try to link A to itself
    val (status, text) = link(a, ids(Seq(a)))
    assert(status == 32, s"should reject self-loop with status 32, got $status")
    assert(text.contains("cycle"), s"status text should mention cycle, got: $text")

    // Verify no self-loop edge
    val selfLoops = table("flows.partitioning_edges")
      .count(s"fk_child_partitioning = $a AND fk_parent_partitioning = $a")
    assert(selfLoops == 0, "should have no self-loop edge")
  }
}

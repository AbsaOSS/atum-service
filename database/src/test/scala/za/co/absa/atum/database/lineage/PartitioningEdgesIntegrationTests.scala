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

package za.co.absa.atum.database.lineage

import io.circe.Json
import io.circe.parser.parse
import za.co.absa.balta.DBTestSuite

import java.time.OffsetDateTime

/**
 *  Integration tests for the explicit lineage edges feature.
 *
 *  Tests verify:
 *  1. Edges are written alongside flows when linking partitionings
 *  2. Direct parent and child queries return correct results
 *  3. Idempotent linking doesn't duplicate edges
 *  4. Cycle detection still works with edges table
 *  5. Descendant pagination and unified lineage graph queries
 */
class PartitioningEdgesIntegrationTests extends DBTestSuite with LineageDBTestSupport {

  private val fncGetDirectParents = "lineage.get_direct_parents"
  private val fncGetDirectChildren = "lineage.get_direct_children"
  private val fncGetDescendants = "lineage.get_partitioning_descendants"
  private val fncGetPartitioningLineageGraph = "lineage.get_partitioning_lineage_graph"

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

  private def countEdges(child: Long): Int = {
    table("lineage.partitioning_edges")
      .count(add("fk_child_partitioning", child))
      .toInt
  }

  private def countFlowMemberships(partitioningId: Long): Int = {
    table("flows.partitioning_to_flow")
      .count(add("fk_partitioning", partitioningId))
      .toInt
  }

  private case class DirectEdgeRow(
    id: Long,
    partitioning: Json,
    author: String,
    linkedBy: Option[String],
    linkedAt: Option[OffsetDateTime]
  )

  private def getDirectParents(childId: Long): Seq[DirectEdgeRow] = {
    function(fncGetDirectParents)
      .setParam("i_partitioning_id", childId)
      .execute { qr =>
        val results = scala.collection.mutable.ArrayBuffer[DirectEdgeRow]()
        while (qr.hasNext) {
          val row = qr.next()
          val status = row.getInt("status").get
          assert(status == 11, s"unexpected status $status from $fncGetDirectParents: ${row.getString("status_text")}")
          val parentId = row.getLong("parent_id").getOrElse(fail("missing parent_id on status-11 row"))
          val partitioning = parse(row.getJsonB("parent_partitioning").get.value)
            .getOrElse(fail("Failed to parse parent_partitioning JSON"))
          val author = row.getString("parent_author").getOrElse(fail("missing parent_author on status-11 row"))
          val linkedBy = row.getString("edge_created_by")
          val linkedAt = row.getOffsetDateTime("edge_created_at")
          results += DirectEdgeRow(parentId, partitioning, author, linkedBy, linkedAt)
        }
        results.toSeq
      }
  }

  private def getDirectChildren(parentId: Long): Seq[DirectEdgeRow] = {
    function(fncGetDirectChildren)
      .setParam("i_partitioning_id", parentId)
      .execute { qr =>
        val results = scala.collection.mutable.ArrayBuffer[DirectEdgeRow]()
        while (qr.hasNext) {
          val row = qr.next()
          val status = row.getInt("status").get
          assert(status == 11, s"unexpected status $status from $fncGetDirectChildren: ${row.getString("status_text")}")
          val childId = row.getLong("child_id").getOrElse(fail("missing child_id on status-11 row"))
          val partitioning = parse(row.getJsonB("child_partitioning").get.value)
            .getOrElse(fail("Failed to parse child_partitioning JSON"))
          val author = row.getString("child_author").getOrElse(fail("missing child_author on status-11 row"))
          val linkedBy = row.getString("edge_created_by")
          val linkedAt = row.getOffsetDateTime("edge_created_at")
          results += DirectEdgeRow(childId, partitioning, author, linkedBy, linkedAt)
        }
        results.toSeq
      }
  }

  test("create partitioning with single parent writes to both edges and flows tables") {
    val aName = uniq("A")
    val a = seed(aName)
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

    // Verify get_direct_parents returns A with metadata
    val parents = getDirectParents(b)
    assert(parents.length == 1, s"should have 1 direct parent, got ${parents.length}")
    assert(parents.head.id == a, s"parent should be $a, got ${parents.head.id}")
    assert(parents.head.partitioning == parse(partitioningJson(aName)).getOrElse(fail("invalid expected JSON")))
    assert(parents.head.author == "Seeder")
    assert(parents.head.linkedBy.contains("Linker"))
    assert(parents.head.linkedAt.isDefined)
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
    val parents = getDirectParents(c).sortBy(_.id)
    assert(parents.length == 2, s"should have 2 direct parents, got ${parents.length}")
    assert(parents(0).id == a, s"first parent should be $a")
    assert(parents(1).id == b, s"second parent should be $b")
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
    val edgesAtoB = table("lineage.partitioning_edges")
      .count(s"fk_child_partitioning = $a AND fk_parent_partitioning = $b")
    assert(edgesAtoB == 0, "should have no edge from B to A")
  }

  test("get_direct_parents returns empty for partitioning without parents") {
    val a = seed(uniq("A"))

    val parents = getDirectParents(a)
    assert(parents.isEmpty, "root partitioning should have no parents")
  }

  test("get_direct_parents returns 41 for non-existent partitioning") {
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
    assert(parentsB.length == 1 && parentsB.head.id == a, "B's parent should be A")

    val parentsC = getDirectParents(c)
    assert(parentsC.length == 1 && parentsC.head.id == b, "C's parent should be B")
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

    val parentsC = getDirectParents(c).sortBy(_.id)
    assert(parentsC.length == 2, s"C should have 2 direct parents, got ${parentsC.length}")
    val expectedParents = Seq(a, b).sorted
    assert(parentsC.map(_.id) == expectedParents, s"C's parents should be $expectedParents")
  }

  test("self-loop constraint prevents A→A edges") {
    val a = seed(uniq("A"))

    // Try to link A to itself
    val (status, text) = link(a, ids(Seq(a)))
    assert(status == 32, s"should reject self-loop with status 32, got $status")
    assert(text.contains("cycle"), s"status text should mention cycle, got: $text")

    // Verify no self-loop edge
    val selfLoops = table("lineage.partitioning_edges")
      .count(s"fk_child_partitioning = $a AND fk_parent_partitioning = $a")
    assert(selfLoops == 0, "should have no self-loop edge")
  }

  test("privileges: lineage.get_direct_parents executable by atum_owner and atum_user only") {
    val sig = "lineage.get_direct_parents(bigint)"
    assert(queryBoolean(s"SELECT has_function_privilege('atum_owner', '$sig', 'EXECUTE')"))
    assert(queryBoolean(s"SELECT has_function_privilege('atum_user', '$sig', 'EXECUTE')"))
    assert(!queryBoolean(s"SELECT has_function_privilege('public', '$sig', 'EXECUTE')"))
    assert(!queryBoolean(s"SELECT has_function_privilege('atum_reader', '$sig', 'EXECUTE')"))
  }

  test("privileges: atum_reader has SELECT on lineage.partitioning_edges") {
    assert(queryBoolean("SELECT has_table_privilege('atum_reader', 'lineage.partitioning_edges', 'SELECT')"))
  }

  test("get_direct_children returns children for a parent partitioning") {
    val p = seed(uniq("Parent"))
    val c1Name = uniq("Child1")
    val c1 = seed(c1Name)
    val c2 = seed(uniq("Child2"))

    link(c1, ids(Seq(p)))
    link(c2, ids(Seq(p)))

    val children = getDirectChildren(p).sortBy(_.id)
    assert(children.length == 2, s"Parent should have 2 children, got ${children.length}")
    assert(children.map(_.id) == Seq(c1, c2).sorted, s"Children should be $c1, $c2")
    val c1Row = children.find(_.id == c1).get
    assert(c1Row.partitioning == parse(partitioningJson(c1Name)).getOrElse(fail("invalid expected JSON")))
    assert(c1Row.author == "Seeder")
    assert(c1Row.linkedBy.contains("Linker"))
    assert(c1Row.linkedAt.isDefined)
  }

  test("get_direct_children returns empty seq when partitioning has no children") {
    val p = seed(uniq("Leaf"))
    val children = getDirectChildren(p)
    assert(children.isEmpty, "Leaf should have 0 children")
  }

  test("get_direct_children returns 41 for non-existent partitioning") {
    function(fncGetDirectChildren)
      .setParam("i_partitioning_id", 999999999L)
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        val status = row.getInt("status").get
        assert(status == 41, s"should return status 41 (not found), got $status")
      }
  }

  private case class DescendantRow(
    status: Int,
    statusText: String,
    descendantId: Option[Long],
    partitioning: Option[Json],
    author: Option[String],
    hasMore: Boolean
  )

  private def getDescendants(
    id: Long,
    limit: Option[Int] = Some(10),
    offset: Option[Long] = Some(0L)
  ): Seq[DescendantRow] = {
    val call = function(fncGetDescendants).setParam("i_id_partitioning", id)
    val withLimit = limit match {
      case Some(l) => call.setParam("i_limit", l)
      case None => call.setParamNull("i_limit")
    }
    val withOffset = offset match {
      case Some(o) => withLimit.setParam("i_offset", o)
      case None => withLimit.setParamNull("i_offset")
    }
    withOffset.execute { qr =>
      val rows = scala.collection.mutable.ListBuffer.empty[DescendantRow]
      while (qr.hasNext) {
        val row = qr.next()
        val partJson = row.getJsonB("partitioning").map { jb =>
          parse(jb.value).getOrElse(fail("Failed to parse descendant partitioning JSON"))
        }
        rows += DescendantRow(
          status = row.getInt("status").get,
          statusText = row.getString("status_text").get,
          descendantId = row.getLong("descendant_id"),
          partitioning = partJson,
          author = row.getString("author"),
          hasMore = row.getBoolean("has_more").getOrElse(false)
        )
      }
      rows.toSeq
    }
  }

  test("get_partitioning_descendants returns all transitive descendants, excluding the partitioning itself") {
    val root = seed(uniq("Root"))
    val childName = uniq("Child")
    val child = seed(childName)
    val grandchildName = uniq("Grandchild")
    val grandchild = seed(grandchildName)
    link(child, ids(Seq(root)))
    link(grandchild, ids(Seq(child)))

    val rows = getDescendants(root)
    assert(rows.forall(r => r.status == 10 && r.statusText == "OK"))
    assert(rows.flatMap(_.descendantId) == Seq(child, grandchild).sorted)
    assert(rows.forall(!_.hasMore))
    assert(rows.forall(_.author.contains("Seeder")))
    val childRow = rows.find(_.descendantId.contains(child)).get
    assert(childRow.partitioning.contains(parse(partitioningJson(childName)).getOrElse(fail("invalid JSON"))))
    val grandchildRow = rows.find(_.descendantId.contains(grandchild)).get
    assert(grandchildRow.partitioning.contains(parse(partitioningJson(grandchildName)).getOrElse(fail("invalid JSON"))))
  }

  test("get_partitioning_descendants supports pagination (i_limit, i_offset, has_more) and clamping") {
    val root = seed(uniq("PageRoot"))
    val descendants = (1 to 3).map { i =>
      val d = seed(uniq(s"PageDesc$i"))
      link(d, ids(Seq(root)))
      d
    }.sorted

    val page1 = getDescendants(root, Some(2), Some(0L))
    assert(page1.flatMap(_.descendantId) == descendants.take(2))
    assert(page1.forall(r => r.status == 10 && r.hasMore))

    val page2 = getDescendants(root, Some(2), Some(2L))
    assert(page2.flatMap(_.descendantId) == descendants.drop(2))
    assert(page2.forall(r => r.status == 10 && !r.hasMore))

    val singleMiddle = getDescendants(root, Some(1), Some(1L))
    assert(singleMiddle.flatMap(_.descendantId) == Seq(descendants(1)))
    assert(singleMiddle.forall(_.hasMore))

    // i_limit = 0 is clamped to 1
    val zeroLimit = getDescendants(root, Some(0), Some(0L))
    assert(zeroLimit.flatMap(_.descendantId) == Seq(descendants.head))
    assert(zeroLimit.forall(r => r.status == 10 && r.hasMore))

    // negative i_offset is clamped to 0
    val negOffset = getDescendants(root, Some(2), Some(-5L))
    assert(negOffset.flatMap(_.descendantId) == descendants.take(2))
    assert(negOffset.forall(r => r.status == 10 && r.hasMore))

    // NULL bounds return all rows with has_more = false
    val unbounded = getDescendants(root, None, None)
    assert(unbounded.flatMap(_.descendantId) == descendants)
    assert(unbounded.forall(r => r.status == 10 && !r.hasMore))

    // offset past the end returns status 14 with no descendant
    val pastEnd = getDescendants(root, Some(10), Some(10L))
    assert(pastEnd.size == 1)
    assert(pastEnd.head.status == 14)
    assert(pastEnd.head.descendantId.isEmpty)
  }

  test("get_partitioning_descendants deduplicates diamond DAG descendants") {
    val root = seed(uniq("DiamondRoot"))
    val left = seed(uniq("DiamondLeft"))
    val right = seed(uniq("DiamondRight"))
    val leaf = seed(uniq("DiamondLeaf"))

    link(left, ids(Seq(root)))
    link(right, ids(Seq(root)))
    link(leaf, ids(Seq(left, right)))

    val rows = getDescendants(root)
    val resultIds = rows.flatMap(_.descendantId)
    assert(rows.forall(_.status == 10))
    assert(resultIds.distinct.size == 3)
    assert(resultIds == Seq(left, right, leaf).sorted)
  }

  test("get_partitioning_descendants returns status 14 for a leaf and 41 for a non-existent partitioning") {
    val leaf = seed(uniq("Leaf"))
    val leafRows = getDescendants(leaf)
    assert(leafRows.size == 1 && leafRows.head.status == 14)

    val missingRows = getDescendants(999999999L)
    assert(missingRows.size == 1 && missingRows.head.status == 41)
  }

  private def callLineageGraph(
    partitioningId: Long,
    direction: String = "both",
    maxDepth: Int = 5
  ): (Int, String, Json) = {
    function(fncGetPartitioningLineageGraph)
      .setParam("i_partitioning_id", partitioningId)
      .setParam("i_direction", direction)
      .setParam("i_max_depth", maxDepth)
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        val status = row.getInt("status").get
        val statusText = row.getString("status_text").get
        val graphStr = row.getString("lineage_graph").getOrElse(fail("expected non-null lineage_graph"))
        val graphJson = parse(graphStr).getOrElse(fail(s"invalid lineage_graph JSON: $graphStr"))
        (status, statusText, graphJson)
      }
  }

  private def graphNodeIds(graph: Json): Set[Long] =
    graph.hcursor.downField("nodes").as[Seq[Json]].getOrElse(fail("missing nodes"))
      .map(_.hcursor.downField("id").as[Long].getOrElse(fail("missing node id")))
      .toSet

  private def graphEdgePairs(graph: Json): Set[(Long, Long)] =
    graph.hcursor.downField("edges").as[Seq[Json]].getOrElse(fail("missing edges"))
      .map { e =>
        val c = e.hcursor
        (
          c.downField("parentPartitioningId").as[Long].getOrElse(fail("missing parentPartitioningId")),
          c.downField("childPartitioningId").as[Long].getOrElse(fail("missing childPartitioningId"))
        )
      }
      .toSet

  test("lineage.get_partitioning_lineage_graph returns unified nodes, edges, and metadata for direction 'both'") {
    val aName = uniq("GraphA")
    val a = seed(aName)
    val b = seed(uniq("GraphB"))
    val c = seed(uniq("GraphC"))

    link(b, ids(Seq(a)))
    link(c, ids(Seq(b)))
    // Add bypass cross-edge A -> C as well, and verify induced cross-edge is included for focal B
    link(c, ids(Seq(a)))

    val (status, _, graph) = callLineageGraph(b, "both", 5)
    assert(status == 11, s"should return status 11, got $status")

    val cursor = graph.hcursor
    assert(cursor.downField("primaryPartitioningId").as[Long].contains(b))
    assert(cursor.downField("depth").as[Int].contains(5))
    assert(cursor.downField("direction").as[String].contains("both"))
    assert(graphNodeIds(graph) == Set(a, b, c))
    assert(graphEdgePairs(graph) == Set((a, b), (b, c), (a, c)))

    val nodeA = cursor.downField("nodes").as[Seq[Json]].getOrElse(Nil)
      .find(_.hcursor.downField("id").as[Long].contains(a))
      .getOrElse(fail("node A missing"))
    assert(nodeA.hcursor.downField("author").as[String].contains("Seeder"))
    assert(nodeA.hcursor.downField("partitioning").as[Json] == parse(partitioningJson(aName)))

    val edges = cursor.downField("edges").as[Seq[Json]].getOrElse(Nil)
    assert(edges.size == 3)
    edges.foreach { edge =>
      val ec = edge.hcursor
      assert(ec.downField("linkedBy").as[String].contains("Linker"))
      assert(ec.downField("linkedAt").as[String].exists(_.nonEmpty))
    }
  }

  test("lineage.get_partitioning_lineage_graph supports 'upstream', 'downstream', and whitespace/uppercase normalization") {
    val a = seed(uniq("DirA"))
    val b = seed(uniq("DirB"))
    val c = seed(uniq("DirC"))

    link(b, ids(Seq(a)))
    link(c, ids(Seq(b)))

    val (sUp, _, upGraph) = callLineageGraph(b, "upstream", 5)
    assert(sUp == 11)
    assert(upGraph.hcursor.downField("direction").as[String].contains("upstream"))
    assert(graphNodeIds(upGraph) == Set(a, b))
    assert(graphEdgePairs(upGraph) == Set((a, b)))

    val (sDown, _, downGraph) = callLineageGraph(b, "downstream", 5)
    assert(sDown == 11)
    assert(downGraph.hcursor.downField("direction").as[String].contains("downstream"))
    assert(graphNodeIds(downGraph) == Set(b, c))
    assert(graphEdgePairs(downGraph) == Set((b, c)))

    val (sNorm, _, normGraph) = callLineageGraph(b, "  UPSTREAM ", 5)
    assert(sNorm == 11)
    assert(normGraph.hcursor.downField("direction").as[String].contains("upstream"))
    assert(graphNodeIds(normGraph) == Set(a, b))
    assert(graphEdgePairs(normGraph) == Set((a, b)))
  }

  test("lineage.get_partitioning_lineage_graph respects i_max_depth truncation") {
    val n1 = seed(uniq("Depth1"))
    val n2 = seed(uniq("Depth2"))
    val n3 = seed(uniq("Depth3"))
    val n4 = seed(uniq("Depth4"))

    link(n2, ids(Seq(n1)))
    link(n3, ids(Seq(n2)))
    link(n4, ids(Seq(n3)))

    val (status, _, graph) = callLineageGraph(n1, "downstream", 1)
    assert(status == 11)
    assert(graph.hcursor.downField("depth").as[Int].contains(1))
    assert(graphNodeIds(graph) == Set(n1, n2))
    assert(graphEdgePairs(graph) == Set((n1, n2)))
  }

  test("lineage.get_partitioning_lineage_graph returns 1 node and empty edges for an isolated node") {
    val isolated = seed(uniq("Isolated"))

    val (status, _, graph) = callLineageGraph(isolated, "both", 5)
    assert(status == 11)
    assert(graphNodeIds(graph) == Set(isolated))
    assert(graph.hcursor.downField("edges").as[Seq[Json]].contains(Seq.empty))
  }

  test("lineage.get_partitioning_lineage_graph deduplicates diamond DAG nodes and edges") {
    val gp = seed(uniq("GraphGP"))
    val p1 = seed(uniq("GraphP1"))
    val p2 = seed(uniq("GraphP2"))
    val child = seed(uniq("GraphChild"))

    link(p1, ids(Seq(gp)))
    link(p2, ids(Seq(gp)))
    link(child, ids(Seq(p1, p2)))

    val (status, _, graph) = callLineageGraph(child, "upstream", 5)
    assert(status == 11)
    val nodes = graph.hcursor.downField("nodes").as[Seq[Json]].getOrElse(fail("missing nodes"))
    val edges = graph.hcursor.downField("edges").as[Seq[Json]].getOrElse(fail("missing edges"))
    assert(nodes.size == 4)
    assert(graphNodeIds(graph) == Set(gp, p1, p2, child))
    assert(edges.size == 4)
    assert(graphEdgePairs(graph) == Set((gp, p1), (gp, p2), (p1, child), (p2, child)))
  }

  test("lineage.get_partitioning_lineage_graph returns 41 for non-existent partitioning") {
    function(fncGetPartitioningLineageGraph)
      .setParam("i_partitioning_id", 999999999L)
      .setParam("i_direction", "both")
      .setParam("i_max_depth", 5)
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        val status = row.getInt("status").get
        assert(status == 41, s"should return status 41 (not found), got $status")
      }
  }

  test("privileges: lineage.get_direct_children executable by atum_owner and atum_user only") {
    val sig = "lineage.get_direct_children(bigint)"
    assert(queryBoolean(s"SELECT has_function_privilege('atum_owner', '$sig', 'EXECUTE')"))
    assert(queryBoolean(s"SELECT has_function_privilege('atum_user', '$sig', 'EXECUTE')"))
    assert(!queryBoolean(s"SELECT has_function_privilege('public', '$sig', 'EXECUTE')"))
    assert(!queryBoolean(s"SELECT has_function_privilege('atum_reader', '$sig', 'EXECUTE')"))
  }

  test("privileges: lineage.get_partitioning_descendants executable by atum_owner and atum_user only") {
    val sig = "lineage.get_partitioning_descendants(bigint, integer, bigint)"
    assert(queryBoolean(s"SELECT has_function_privilege('atum_owner', '$sig', 'EXECUTE')"))
    assert(queryBoolean(s"SELECT has_function_privilege('atum_user', '$sig', 'EXECUTE')"))
    assert(!queryBoolean(s"SELECT has_function_privilege('public', '$sig', 'EXECUTE')"))
    assert(!queryBoolean(s"SELECT has_function_privilege('atum_reader', '$sig', 'EXECUTE')"))
  }

  test("privileges: lineage.get_partitioning_lineage_graph executable by atum_owner and atum_user only") {
    val sig = "lineage.get_partitioning_lineage_graph(bigint, text, integer)"
    assert(queryBoolean(s"SELECT has_function_privilege('atum_owner', '$sig', 'EXECUTE')"))
    assert(queryBoolean(s"SELECT has_function_privilege('atum_user', '$sig', 'EXECUTE')"))
    assert(!queryBoolean(s"SELECT has_function_privilege('public', '$sig', 'EXECUTE')"))
    assert(!queryBoolean(s"SELECT has_function_privilege('atum_reader', '$sig', 'EXECUTE')"))
  }
}

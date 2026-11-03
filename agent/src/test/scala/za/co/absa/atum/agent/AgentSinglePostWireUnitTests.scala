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

package za.co.absa.atum.agent

import com.typesafe.config.ConfigFactory
import io.circe.JsonObject
import io.circe.parser.parse
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.exception.AtumAgentException.HttpException
import za.co.absa.atum.model.dto.{AtumContextDTO, PartitioningSubmitV2DTO}
import za.co.absa.atum.model.types.basic.{AtumPartitions, AtumPartitionsOps}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.{JsonDeserializationSyntax, JsonSerializationSyntax}

import java.net.URLDecoder

/**
 *  Real-HTTP wire tests of the single-POST sub-context creation: a real agent (`AtumAgent.fromConfig`, real
 *  [[za.co.absa.atum.agent.dispatcher.HttpDispatcher]] + OkHttp over a loopback TCP socket) talks to
 *  [[AtumV2StubServer]], a recording stub of the Phase 9 server contract. No Mockito backend, no atum-server, no DB.
 *
 *  Named `*UnitTests` on purpose so that it is part of the standard `test` / `testAgentSpark*` gates.
 */
class AgentSinglePostWireUnitTests extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private val Author = "wire-author"
  private val ExpectedKeys = Set("partitioning", "parentPartitioningIds", "author", "copyMeasureDefinitions")

  private var server: AtumV2StubServer = _
  private var agent: AtumAgent = _

  override def beforeEach(): Unit = {
    server = new AtumV2StubServer
    server.start()
    val url = server.baseUrl
    val config = ConfigFactory.parseString(
      List(
        "atum.dispatcher.type=http",
        "atum.dispatcher.http.url=" + "\"" + url + "\"",
        "atum.author=" + Author
      ).mkString("\n")
    )
    agent = AtumAgent.fromConfig(config)
  }

  override def afterEach(): Unit = {
    if (server != null) {
      server.stop()
      server = null
    }
  }

  private val parent1 = AtumPartitions("par" -> "1")
  private val parent2 = AtumPartitions("par" -> "2")
  private val parent3 = AtumPartitions("par" -> "3")
  private val sub = AtumPartitions("sub" -> "x")

  /** Registers the parent in the stub under `id` and returns its context without any request being made. */
  private def parentContext(partitions: AtumPartitions, id: Long): AtumContext = {
    server.register(partitions.toPartitioningDTO, id)
    AtumContext.fromDTO(AtumContextDTO(partitions.toPartitioningDTO), agent)
  }

  private def kind(r: RecordedRequest): String = {
    if (r.path.endsWith("/measures")) s"${r.method} measures"
    else if (r.path.endsWith("/additional-data")) s"${r.method} additional-data"
    else if (r.path.endsWith("/ancestors")) s"${r.method} ancestors"
    else if (r.method == "GET" && r.query.exists(_.startsWith("partitioning="))) "GET lookup"
    else s"${r.method} partitionings"
  }

  private def kinds: List[String] = server.requests.map(kind)

  private def lookups: List[String] =
    server.requests.filter(kind(_) == "GET lookup").flatMap(_.query).map { q =>
      URLDecoder.decode(q.stripPrefix("partitioning="), "UTF-8")
    }

  private def posts = server.requests.filter(kind(_) == "POST partitionings")

  private def rawJson(request: RecordedRequest): JsonObject =
    parse(request.body.get).toOption.get.asObject.get

  private def singlePostJson: JsonObject = {
    posts should have size 1
    rawJson(posts.head)
  }

  private def parentIdsIn(json: JsonObject): Seq[Long] =
    json("parentPartitioningIds").get.asArray.get.map(_.asNumber.get.toLong.get)

  /** n lookups (input order, never the child), one POST, measures, additional data; never a PATCH. */
  private def assertOnePostNoPatch(parentsInOrder: Seq[AtumPartitions], child: AtumPartitions): Unit = {
    val expectedKinds =
      List.fill(parentsInOrder.size)("GET lookup") ++ List("POST partitionings", "GET measures", "GET additional-data")
    kinds shouldBe expectedKinds
    lookups shouldBe parentsInOrder.map(_.toPartitioningDTO.asBase64EncodedJsonString).toList
    lookups should not contain child.toPartitioningDTO.asBase64EncodedJsonString
    server.requests.count(_.method == "PATCH") shouldBe 0
    posts should have size 1
  }

  private def assertBody(json: JsonObject, child: AtumPartitions, parentIds: Seq[Long], flag: Boolean): Unit = {
    json.keys.toSet shouldBe ExpectedKeys
    json.keys should not contain "parentPartitioningId"
    json.keys should not contain "parentPartitioning"
    json.keys should not contain "additionalData"
    json.keys should not contain "measures"
    parentIdsIn(json) shouldBe parentIds
    json("copyMeasureDefinitions").get.isBoolean shouldBe true
    json("copyMeasureDefinitions").get.asBoolean shouldBe Some(flag)
    json("author").get.asString shouldBe Some(Author)
    json("partitioning").get.noSpaces shouldBe child.toPartitioningDTO.asJsonString
  }

  "A real agent" should "send exactly one POST for a root partitioning (0 parents)" in {
    val child = AtumPartitions("root" -> "0")

    val context = agent.getOrCreateAtumContext(child)

    context.atumPartitions shouldBe child
    kinds shouldBe List("POST partitionings", "GET measures", "GET additional-data")
    server.requests.head.answeredStatus shouldBe 201
    server.requests.count(_.method == "PATCH") shouldBe 0
    val json = singlePostJson
    assertBody(json, child, Seq.empty, flag = false)
    json("parentPartitioningIds").get.asArray.get shouldBe empty
    json.keys should not contain "additionalData"
  }

  it should "send one lookup, one POST and no PATCH for a parent created through the real agent" in {
    val parentPartitions = AtumPartitions("realpar" -> "1")
    val parent = agent.getOrCreateAtumContext(parentPartitions)
    val parentId = server.idOf(parentPartitions.toPartitioningDTO).get
    server.clearRequests()

    val child = agent.getOrCreateAtumSubContext(sub)(parent)

    child.atumPartitions shouldBe (parentPartitions ++ sub)
    assertOnePostNoPatch(Seq(parentPartitions), parentPartitions ++ sub)
    assertBody(singlePostJson, parentPartitions ++ sub, Seq(parentId), flag = false)
  }

  // (label, call, mergeWithParent, expected flag): single parent (id 7), pre-existing overloads send false,
  // the new overloads send what they are told.
  private def singleParentCases(): Seq[(String, AtumAgent => AtumContext => AtumContext, Boolean, Boolean)] = Seq(
    ("agent 1-arg overload", a => p => a.getOrCreateAtumSubContext(sub)(p), true, false),
    ("agent 2-arg overload mergeWithParent = true", a => p => a.getOrCreateAtumSubContext(sub, true)(p), true, false),
    (
      "agent 2-arg overload mergeWithParent = false",
      a => p => a.getOrCreateAtumSubContext(sub, mergeWithParent = false)(p),
      false,
      false
    ),
    ("AtumContext 1-arg subPartitionContext", _ => p => p.subPartitionContext(sub), true, false),
    (
      "AtumContext 2-arg subPartitionContext mergeWithParent = false",
      _ => p => p.subPartitionContext(sub, mergeWithParent = false),
      false,
      false
    ),
    (
      "NEW agent 3-arg overload (merge = true, copy = true)",
      a => p => a.getOrCreateAtumSubContext(sub, true, true)(p),
      true,
      true
    ),
    (
      "NEW agent 3-arg overload (merge = false, copy = true)",
      a => p => a.getOrCreateAtumSubContext(sub, false, true)(p),
      false,
      true
    ),
    (
      "NEW agent 3-arg overload (merge = true, copy = false)",
      a => p => a.getOrCreateAtumSubContext(sub, true, false)(p),
      true,
      false
    ),
    (
      "NEW AtumContext 3-arg subPartitionContext (merge = true, copy = true)",
      _ => p => p.subPartitionContext(sub, true, true),
      true,
      true
    ),
    (
      "NEW AtumContext 3-arg subPartitionContext (merge = false, copy = true)",
      _ => p => p.subPartitionContext(sub, false, true),
      false,
      true
    )
  )

  singleParentCases().foreach { case (label, call, merge, flag) =>
    it should s"send one lookup, ONE POST (flag = $flag) and no PATCH for 1 parent via $label" in {
      val parentId = 7L
      val parent = parentContext(parent1, parentId)
      val expectedChild = if (merge) parent1 ++ sub else sub

      val context = call(agent)(parent)

      context.atumPartitions shouldBe expectedChild
      assertOnePostNoPatch(Seq(parent1), expectedChild)
      server.requests.find(kind(_) == "POST partitionings").get.answeredStatus shouldBe 201
      val json = singlePostJson
      assertBody(json, expectedChild, Seq(parentId), flag)
      posts.head.body.get.as[PartitioningSubmitV2DTO] shouldBe
        PartitioningSubmitV2DTO(expectedChild.toPartitioningDTO, Seq(parentId), Author, flag)
    }
  }

  // (label, explicit copyMeasureDefinitions argument (None = pre-existing overload), expected flag in the body)
  Seq[(String, Option[Boolean], Boolean)](
    ("pre-existing multi-parent overload", None, false),
    ("NEW multi-parent overload (copy = true)", Some(true), true),
    ("NEW multi-parent overload (copy = false)", Some(false), false)
  ).foreach { case (label, copyArg, flag) =>
    it should s"send 3 lookups in order, ONE POST (flag = $flag) and no PATCH for 3 parents via $label" in {
      val parents = Seq(parentContext(parent1, 11L), parentContext(parent2, 12L), parentContext(parent3, 13L))
      val context = copyArg match {
        case None => agent.getOrCreateAtumSubContext(sub, parents)
        case Some(copy) => agent.getOrCreateAtumSubContext(sub, parents, copy)
      }

      context.atumPartitions shouldBe sub
      assertOnePostNoPatch(Seq(parent1, parent2, parent3), sub)
      server.requests.find(kind(_) == "POST partitionings").get.answeredStatus shouldBe 201
      assertBody(singlePostJson, sub, Seq(11L, 12L, 13L), flag)
    }
  }

  it should "collapse duplicate parents to the distinct ids (first occurrence order kept)" in {
    val p1 = parentContext(parent1, 11L)
    val p2 = parentContext(parent2, 12L)
    val p3 = parentContext(parent3, 13L)

    agent.getOrCreateAtumSubContext(sub, Seq(p2, p1, p2, p3, p1), true)

    assertOnePostNoPatch(Seq(parent2, parent1, parent3), sub)
    assertBody(singlePostJson, sub, Seq(12L, 11L, 13L), flag = true)
  }

  it should "round-trip the raw POST body of 3 parents / flag = true through the PartitioningSubmitV2DTO decoder" in {
    val parents = Seq(parentContext(parent1, 11L), parentContext(parent2, 12L), parentContext(parent3, 13L))

    agent.getOrCreateAtumSubContext(sub, parents, true)

    val body = posts.head.body.get
    body.as[PartitioningSubmitV2DTO] shouldBe
      PartitioningSubmitV2DTO(sub.toPartitioningDTO, Seq(11L, 12L, 13L), Author, copyMeasureDefinitions = true)
    val json = rawJson(posts.head)
    json.keys should not contain "parentPartitioningId"
    json.keys should not contain "parentPartitioning"
    json("copyMeasureDefinitions").get.isBoolean shouldBe true
    json("copyMeasureDefinitions").get.asBoolean shouldBe Some(true)
    json.keys.toSet shouldBe ExpectedKeys
  }

  it should "send one POST per repeated call: 201 first, 200 for the existing partitioning, no PATCH, no child GET" in {
    val parent = parentContext(parent1, 7L)

    val first = agent.getOrCreateAtumSubContext(sub, mergeWithParent = false)(parent)
    val firstKinds = kinds
    val firstAnswer = posts.map(_.answeredStatus)
    server.clearRequests()
    val second = agent.getOrCreateAtumSubContext(sub, mergeWithParent = false)(parent)

    firstKinds shouldBe List("GET lookup", "POST partitionings", "GET measures", "GET additional-data")
    firstAnswer shouldBe List(201)
    kinds shouldBe List("GET lookup", "POST partitionings", "GET measures", "GET additional-data")
    posts.map(_.answeredStatus) shouldBe List(200)
    lookups should not contain sub.toPartitioningDTO.asBase64EncodedJsonString
    server.requests.count(_.method == "PATCH") shouldBe 0
    first.atumPartitions shouldBe sub
    second.atumPartitions shouldBe first.atumPartitions
  }

  it should "surface a 409 as HttpException after exactly one POST (no retry, no PATCH)" in {
    val parent = parentContext(parent1, 7L)
    server.failNextPostWith(409)

    val thrown = the[HttpException] thrownBy agent.getOrCreateAtumSubContext(sub, true, true)(parent)

    thrown.statusCode shouldBe 409
    kinds shouldBe List("GET lookup", "POST partitionings")
    posts should have size 1
    posts.head.answeredStatus shouldBe 409
    server.requests.count(_.method == "PATCH") shouldBe 0
  }

  it should "abort with HttpException and no POST when the (only) parent is unknown (404)" in {
    val unknown = AtumPartitions("unknown" -> "parent")
    val parent = AtumContext.fromDTO(AtumContextDTO(unknown.toPartitioningDTO), agent)

    val thrown = the[HttpException] thrownBy agent.getOrCreateAtumSubContext(sub)(parent)

    thrown.statusCode shouldBe 404
    kinds shouldBe List("GET lookup")
    server.requests.head.answeredStatus shouldBe 404
    posts shouldBe empty
  }

  it should "abort with HttpException and no POST when one of several parents is unknown (404)" in {
    val known = parentContext(parent1, 11L)
    val unknown = AtumContext.fromDTO(AtumContextDTO(parent2.toPartitioningDTO), agent)

    val thrown = the[HttpException] thrownBy agent.getOrCreateAtumSubContext(sub, Seq(known, unknown), true)

    thrown.statusCode shouldBe 404
    kinds shouldBe List("GET lookup", "GET lookup")
    server.requests.map(_.answeredStatus) shouldBe List(200, 404)
    posts shouldBe empty
  }

  it should "never put additional data or measures into the POST body" in {
    val parent = parentContext(parent1, 7L)

    agent.getOrCreateAtumSubContext(sub, true, true)(parent)

    val json = singlePostJson
    json.keys should not contain "additionalData"
    json.keys should not contain "measures"
    json.keys.toSet shouldBe ExpectedKeys
  }
}

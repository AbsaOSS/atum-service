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

import org.apache.spark.sql.types.{DoubleType, StringType, StructField, StructType}
import org.apache.spark.sql.{Row, SparkSession}
import za.co.absa.atum.agent.model.AtumMeasure.RecordCount
import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import za.co.absa.atum.agent.dispatcher.{HttpDispatcher, HttpRetry}

import io.circe.parser.decode
import za.co.absa.atum.model.dto.{ChildPartitioningDTO, LineageGraphDTO, ParentPartitioningDTO, PartitioningWithIdDTO}
import za.co.absa.atum.model.envelopes.SuccessResponse.{MultiSuccessResponse, PaginatedResponse, SingleSuccessResponse}

import sttp.capabilities
import sttp.client3._
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.model.Uri
import scala.collection.immutable.ListMap
import za.co.absa.atum.agent.exception.AtumAgentException.HttpException

/**
 *  Mimics the slice of `java.net.http.HttpHeaders` these tests need. Top-level (not nested in the test class) so
 *  the synthesized case class equality doesn't need an outer-instance check.
 */
private final case class CompatHeaders(values: Seq[(String, String)]) {
  def firstValue(name: String): java.util.Optional[String] =
    java.util.Optional.ofNullable(values.collectFirst { case (k, v) if k.equalsIgnoreCase(name) => v }.orNull)
}

/** Minimal response wrapper exposing `.statusCode()` / `.body()` / `.headers()` as java.net.http's did. */
private final case class CompatResponse(code: Int, payload: String, allHeaders: Seq[(String, String)]) {
  def statusCode(): Int = code
  def body(): String = payload
  def headers(): CompatHeaders = CompatHeaders(allHeaders)
}

class AgentServerCompatibilityTests extends DBTestSuite {

  private val serverUrl = "http://localhost:8080"

  // sttp/OkHttp is used instead of java.net.http: the agent module (and its tests) must stay Java-8-compilable,
  // and java.net.http only exists from Java 11 onwards.
  private val httpBackend: SttpBackend[Identity, capabilities.WebSockets] = OkHttpSyncBackend()

  private def httpCall(method: String, path: String, jsonBody: Option[String] = None): CompatResponse = {
    val uri = Uri.unsafeParse(s"$serverUrl$path")
    val base = (method match {
      case "GET" => basicRequest.get(uri)
      case "POST" => basicRequest.post(uri)
      case "PATCH" => basicRequest.patch(uri)
    }).header("Content-Type", "application/json")
    val withBody = jsonBody.fold(base)(base.body(_))
    val response = withBody.response(asString).send(httpBackend)
    CompatResponse(response.code.code, response.body.merge, response.headers.map(h => h.name -> h.value))
  }

  // Agent writes are committed by the server and not rolled back by balta, so partition values are unique
  // per run to keep the tests re-runnable (and runnable on several build axes) against the same database.
  private val runSuffix = System.currentTimeMillis().toString

  private val testDataForRDD = Seq(
    Row("A", 8.0),
    Row("B", 2.9),
    Row("C", 9.1),
    Row("D", 2.5)
  )

  private val testDataSchema = new StructType()
    .add(StructField("notImportantColumn", StringType))
    .add(StructField("columnForSum", DoubleType))

  private val expectedMeasurement = JsonBString(
    """{"mainValue": {"value": "4", "valueType": "Long"}, "supportValues": {}}""".stripMargin
  )

  /**
   *  Runs the full agent -> server flow for the given `agent` and `partitioning` and asserts that the
   *  server persisted everything correctly.
   *
   *  Every DB assertion is scoped to the partitioning created by this flow (via `fk_partitioning`), so
   *  multiple compatibility tests can run against the same shared database without their writes
   *  interfering with each other's assertions. Note: the agent writes go through the server on its own
   *  DB connection and are therefore committed (not rolled back by balta between tests).
   */
  private def verifyAgentServerCompatibility(agent: AtumAgent, partitioning: ListMap[String, String]): Unit = {
    val spark = SparkSession
      .builder()
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    val rdd = spark.sparkContext.parallelize(testDataForRDD)
    val df = spark.createDataFrame(rdd, testDataSchema)

    // Atum Context stuff - Partitioning, Measures, Additional Data, Checkpoint
    val atumContext = agent.getOrCreateAtumContext(partitioning)

    atumContext.addMeasure(RecordCount())
    atumContext.addAdditionalData("author", "Laco")
    atumContext.addAdditionalData(Map("author" -> "LacoNew", "version" -> "1.0"))

    atumContext.createCheckpoint("checkPointNameCount", df)

    // The expected JSONB matches PostgreSQL's canonical jsonb text output (keys ordered by length,
    // single space after ':' and ','), reconstructed from the partitioning used in the test.
    val keysJson = partitioning.keys.map(key => s""""$key"""").mkString("[", ", ", "]")
    val keysToValuesMapJson = partitioning
      .map { case (key, value) => s""""$key": "$value"""" }
      .mkString("{", ", ", "}")
    val expectedPartitioning = JsonBString(
      s"""{"keys": $keysJson, "version": 1, "keysToValuesMap": $keysToValuesMapJson}"""
    )

    // DB Check, data should be written in the DB. Resolve the partitioning row (and its id) by JSONB
    // containment so all following assertions can be scoped to exactly this partitioning.
    val partitioningId = table("runs.partitionings")
      .where(s"""partitioning @> '{"keysToValuesMap": $keysToValuesMapJson}'::jsonb""") { partitioningsResult =>
        assert(partitioningsResult.hasNext, "the partitioning should have been created on the server")
        val row = partitioningsResult.next()

        // All column reads must happen before advancing the cursor: balta's QueryResultRow reads
        // lazily from the live JDBC ResultSet, and hasNext calls resultSet.next(), which moves the
        // cursor off this row (any read afterwards throws "ResultSet not positioned properly").
        assert(row.getJsonB("partitioning").contains(expectedPartitioning))
        val id = row.getLong("id_partitioning").get
        assert(!partitioningsResult.hasNext)

        id
      }

    table("runs.additional_data").where(s"fk_partitioning = $partitioningId") { adResult =>
      val expectedMap = Map("author" -> "LacoNew", "version" -> "1.0")

      assert(adResult.hasNext)
      val row = adResult.next()

      val adName1 = row.getString("ad_name").get
      val adValue1 = row.getString("ad_value").get

      assert(expectedMap(adName1) == adValue1)

      assert(adResult.hasNext)
      val row2 = adResult.next()

      val adName2 = row2.getString("ad_name").get
      val adValue2 = row2.getString("ad_value").get

      assert(expectedMap(adName2) == adValue2)

      assert(!adResult.hasNext)
    }

    table("runs.additional_data_history").where(s"fk_partitioning = $partitioningId") { adHistResult =>
      assert(adHistResult.hasNext)
      val row = adHistResult.next()

      assert(row.getString("ad_name").contains("author"))
      assert(row.getString("ad_value").contains("Laco"))

      assert(!adHistResult.hasNext)
    }

    val measureDefinitionId = table("runs.measure_definitions").where(s"fk_partitioning = $partitioningId") {
      measureDefResult =>
        assert(measureDefResult.hasNext)
        val row = measureDefResult.next()

        assert(row.getString("measure_name").contains("count"))
        assert(row.getString("measured_columns").contains("{}"))
        val id = row.getLong("id_measure_definition").get
        assert(!measureDefResult.hasNext)

        id
    }

    table("runs.measurements").where(s"fk_measure_definition = $measureDefinitionId") { measurementsResult =>
      assert(measurementsResult.hasNext)
      val row = measurementsResult.next()

      assert(row.getJsonB("measurement_value").contains(expectedMeasurement))
      assert(!measurementsResult.hasNext)
    }
  }

  // Need to add service & pg run in CI
  test("Agent should be compatible with server using a directly injected HTTP dispatcher") {
    // Agent configured to work with HTTP Dispatcher and service on localhost
    val agent: AtumAgent = new AtumAgent {
      override val dispatcher: HttpDispatcher = new HttpDispatcher(
        ConfigFactory
          .empty()
          .withValue("atum.dispatcher.type", ConfigValueFactory.fromAnyRef("http"))
          .withValue("atum.dispatcher.http.url", ConfigValueFactory.fromAnyRef(serverUrl))
      )
    }

    val domainAtumPartitioning = ListMap(
      "partition1" -> s"valueFromTest1-$runSuffix",
      "partition2" -> s"valueFromTest2-$runSuffix"
    )

    verifyAgentServerCompatibility(agent, domainAtumPartitioning)
  }

  // Need to add service & pg run in CI
  test("Agent built via AtumAgent.fromConfig with custom (non-default) values should be compatible with server") {
    // A fully custom config with non-default retry and timeout settings, loaded from a dedicated
    // resource that is parsed explicitly (NOT reference.conf / application.conf), so it never leaks
    // into ConfigFactory.load() used by other tests. This exercises the real "config parsed from a
    // file" path together with AtumAgent.fromConfig -> dispatcherFromConfig -> new HttpDispatcher and
    // HttpRetry.fromConfig, proving the agent still talks to the server when none of the built-in
    // defaults are used.
    val customConfig: Config = ConfigFactory.parseResources("agent-compat.conf").resolve()

    val agent: AtumAgent = AtumAgent.fromConfig(customConfig)

    // The factory must have selected the HTTP dispatcher based on the custom config.
    agent.dispatcher match {
      case _: HttpDispatcher => // expected
      case other => fail(s"Expected an HttpDispatcher, but got ${other.getClass.getName}")
    }

    // The custom retry values must have been read from the config and must differ from the defaults,
    // confirming the non-default configuration is actually taking effect (not silently falling back).
    val retry = HttpRetry.fromConfig(customConfig)
    assert(retry.maxRetries == 5)
    assert(retry.initialDelay == 250L)
    assert(retry.maxDelay == 8000L)
    assert(retry != HttpRetry.Default)

    val domainAtumPartitioning = ListMap(
      "customPartition1" -> s"customValueFromTest1-$runSuffix",
      "customPartition2" -> s"customValueFromTest2-$runSuffix"
    )

    verifyAgentServerCompatibility(agent, domainAtumPartitioning)
  }

  // Need to add service & pg run in CI
  test("Agent multi-parent getOrCreateAtumSubContext should create one child linked to both parents on the server") {
    val agent: AtumAgent = new AtumAgent {
      override val dispatcher: HttpDispatcher = new HttpDispatcher(
        ConfigFactory
          .empty()
          .withValue("atum.dispatcher.type", ConfigValueFactory.fromAnyRef("http"))
          .withValue("atum.dispatcher.http.url", ConfigValueFactory.fromAnyRef(serverUrl))
      )
    }

    // Unique values per run: agent writes are committed by the server and not rolled back by balta.
    val suffix = System.currentTimeMillis().toString
    val parentAMap = ListMap("mpParentA" -> s"a$suffix")
    val parentBMap = ListMap("mpParentB" -> s"b$suffix")
    val childMap = ListMap("mpChild" -> s"c$suffix")

    def containment(partitioning: ListMap[String, String]): String = {
      val json = partitioning.map { case (k, v) => s""""$k": "$v"""" }.mkString("{", ", ", "}")
      s"""partitioning @> '{"keysToValuesMap": $json}'::jsonb"""
    }

    def partitioningIds(partitioning: ListMap[String, String]): Seq[Long] =
      table("runs.partitionings").where(containment(partitioning)) { result =>
        var ids = Vector.empty[Long]
        // read the id before advancing the cursor (balta reads lazily from the live ResultSet)
        while (result.hasNext) {
          ids = ids :+ result.next().getLong("id_partitioning").get
        }
        ids
      }

    val parentA = agent.getOrCreateAtumContext(parentAMap)
    val parentB = agent.getOrCreateAtumContext(parentBMap)

    assert(partitioningIds(childMap).isEmpty, "the child must not exist before the call")

    val child = agent.getOrCreateAtumSubContext(childMap, Seq(parentA, parentB))

    // Repeated call with duplicated parents must be idempotent (same child, no new rows).
    val childAgain = agent.getOrCreateAtumSubContext(childMap, Seq(parentA, parentB, parentA))
    assert(childAgain.atumPartitions == child.atumPartitions)

    val idsA = partitioningIds(parentAMap)
    val idsB = partitioningIds(parentBMap)
    val idsChild = partitioningIds(childMap)
    assert(idsA.size == 1 && idsB.size == 1, "each parent exists exactly once")
    assert(idsChild.size == 1, "exactly one child must exist")
    val (idA, idB, childId) = (idsA.head, idsB.head, idsChild.head)

    // PRIMARY: server read path - ancestors of the child are exactly {A, B}, no duplicates.
    val response = httpCall("GET", s"/api/v2/partitionings/$childId/ancestors?limit=1000")
    assert(response.statusCode() == 200, s"unexpected response: ${response.body()}")
    val ancestorIds = decode[PaginatedResponse[PartitioningWithIdDTO]](response.body()) match {
      case Right(parsed) => parsed.data.map(_.id)
      case Left(error) => fail(s"cannot decode ancestors response: $error")
    }
    assert(ancestorIds.toSet == Set(idA, idB))
    assert(ancestorIds.size == 2)
    assert(!ancestorIds.contains(childId))

    // SECONDARY: flows - the child belongs to own flow + A's + B's primary flows, without duplicate links.
    def primaryFlowId(partitioningId: Long): Long =
      table("flows.flows").where(s"fk_primary_partitioning = $partitioningId") { result =>
        assert(result.hasNext)
        val id = result.next().getLong("id_flow").get
        assert(!result.hasNext)
        id
      }

    val flowA = primaryFlowId(idA)
    val flowB = primaryFlowId(idB)
    val childFlows = table("flows.partitioning_to_flow").where(s"fk_partitioning = $childId") { result =>
      var flows = Vector.empty[Long]
      while (result.hasNext) {
        flows = flows :+ result.next().getLong("fk_flow").get
      }
      flows
    }
    assert(childFlows.contains(flowA))
    assert(childFlows.contains(flowB))
    assert(childFlows.distinct.size == childFlows.size, "no duplicate (fk_flow, fk_partitioning) links")

    // DIRECT PARENTS: new endpoint verification
    val parentsResponse = httpCall("GET", s"/api/v2/partitionings/$childId/parents")
    assert(parentsResponse.statusCode() == 200, s"unexpected response: ${parentsResponse.body()}")
    val directParents = decode[MultiSuccessResponse[ParentPartitioningDTO]](parentsResponse.body()) match {
      case Right(parsed) => parsed.data
      case Left(error) => fail(s"cannot decode direct parents response: $error")
    }
    assert(directParents.size == 2)
    assert(directParents.map(_.id).toSet == Set(idA, idB))
    assert(directParents.forall(_.linkedBy.isDefined))
    assert(directParents.forall(_.linkedAt.isDefined))

    // LINEAGE EDGES: database verification
    assert(table("flows.partitioning_edges").count(s"fk_child_partitioning = $childId") == 2)
    val parentEdgeIds = table("flows.partitioning_edges").where(s"fk_child_partitioning = $childId") { result =>
      var ids = Vector.empty[Long]
      while (result.hasNext) {
        ids = ids :+ result.next().getLong("fk_parent_partitioning").get
      }
      ids
    }
    assert(parentEdgeIds.toSet == Set(idA, idB))
    assert(parentEdgeIds.contains(idA) && parentEdgeIds.contains(idB))

    // DIRECT CHILDREN: endpoint verification (idA's child must be childId)
    val childrenResponse = httpCall("GET", s"/api/v2/partitionings/$idA/children")
    assert(childrenResponse.statusCode() == 200, s"unexpected response: ${childrenResponse.body()}")
    val directChildren = decode[MultiSuccessResponse[ChildPartitioningDTO]](childrenResponse.body()) match {
      case Right(parsed) => parsed.data
      case Left(error) => fail(s"cannot decode direct children response: $error")
    }
    assert(directChildren.exists(_.id == childId))

    // LINEAGE GRAPH: endpoint verification
    val lineageResponse = httpCall("GET", s"/api/v2/partitionings/$childId/lineage")
    assert(lineageResponse.statusCode() == 200, s"unexpected response: ${lineageResponse.body()}")
    val lineageGraph = decode[SingleSuccessResponse[LineageGraphDTO]](lineageResponse.body()) match {
      case Right(parsed) => parsed.data
      case Left(error) => fail(s"cannot decode lineage graph response: $error")
    }
    assert(lineageGraph.primaryPartitioningId == childId)
    assert(lineageGraph.nodes.map(_.id).toSet.contains(childId))
    assert(lineageGraph.edges.size >= 2)
  }

  // ---------------------------------------------------------------------------------------------------------
  // VERIFY-01 (Phase 11): v1.1 scenarios - consolidated idempotent POST, single new parent for an existing
  // child, copyMeasureDefinitions true / false / default, cycle -> 409, unknown parent -> 404.
  // All keys are unique per run (`v11Run`) and every DB assertion is scoped by the partitioning id.
  // ---------------------------------------------------------------------------------------------------------

  private def v11Agent(): AtumAgent = new AtumAgent {
    override val dispatcher: HttpDispatcher = new HttpDispatcher(
      ConfigFactory
        .empty()
        .withValue("atum.dispatcher.type", ConfigValueFactory.fromAnyRef("http"))
        .withValue("atum.dispatcher.http.url", ConfigValueFactory.fromAnyRef(serverUrl))
    )
  }

  // Unique per JVM run: millis + a random part, so two build axes started in the same millisecond (or a re-run) never
  // share a key. The first POST of a test asserts a strict 201, a shared key would turn it into a spurious 200.
  private val v11Run: String = s"${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString.take(8)}"

  // A probe id that no database sequence reaches (Number.MAX_SAFE_INTEGER, still a valid Long).
  private val v11UnknownId: Long = 9007199254740991L

  // Author of the parent's measure definitions in the copy test, deliberately different from the agent user, so that
  // "created_by = caller" can be told apart from "created_by = author of the source definition".
  private val v11ParentAuthor = s"compat-parent-author-$v11Run"

  private def v11JsonEscape(value: String): String = value.flatMap {
    case '"' => "\\\""
    case '\\' => "\\\\"
    case c if c < ' ' => "\\" + "u" + "%04x".format(c.toInt)
    case c => c.toString
  }

  private def v11SqlEscape(value: String): String = value.replace("'", "''")

  private def v11Containment(partitioning: ListMap[String, String]): String = {
    val json = partitioning
      .map { case (k, v) => s""""${v11JsonEscape(k)}": "${v11JsonEscape(v)}"""" }
      .mkString("{", ", ", "}")
    s"""partitioning @> '{"keysToValuesMap": ${v11SqlEscape(json)}}'::jsonb"""
  }

  private def v11PartitioningIds(partitioning: ListMap[String, String]): Seq[Long] =
    table("runs.partitionings").where(v11Containment(partitioning)) { result =>
      var ids = Vector.empty[Long]
      // read the id before advancing the cursor (balta reads lazily from the live ResultSet)
      while (result.hasNext) {
        ids = ids :+ result.next().getLong("id_partitioning").get
      }
      ids
    }

  private def v11PrimaryFlowId(partitioningId: Long): Long =
    table("flows.flows").where(s"fk_primary_partitioning = $partitioningId") { result =>
      assert(result.hasNext)
      val id = result.next().getLong("id_flow").get
      assert(!result.hasNext)
      id
    }

  private def v11FlowsOf(partitioningId: Long): Seq[Long] =
    table("flows.partitioning_to_flow").where(s"fk_partitioning = $partitioningId") { result =>
      var flows = Vector.empty[Long]
      while (result.hasNext) {
        flows = flows :+ result.next().getLong("fk_flow").get
      }
      flows
    }

  private def v11MeasureDefinitions(partitioningId: Long): Seq[(String, String, String)] =
    table("runs.measure_definitions").where(s"fk_partitioning = $partitioningId") { result =>
      var rows = Vector.empty[(String, String, String)]
      while (result.hasNext) {
        val row = result.next()
        rows = rows :+ ((
          row.getString("measure_name").get,
          row.getString("measured_columns").get,
          row.getString("created_by").get
        ))
      }
      rows
    }

  private def v11AdditionalDataCount(partitioningId: Long): Int =
    table("runs.additional_data").where(s"fk_partitioning = $partitioningId") { result =>
      var count = 0
      while (result.hasNext) {
        result.next()
        count += 1
      }
      count
    }

  private def v11PostBody(partitioning: ListMap[String, String], parentIds: Seq[Long], author: String): String = {
    val keyValues = partitioning
      .map { case (k, v) => s"""{"key":"${v11JsonEscape(k)}","value":"${v11JsonEscape(v)}"}""" }
      .mkString("[", ",", "]")
    s"""{"partitioning":$keyValues,"parentPartitioningIds":${parentIds.mkString(
        "[",
        ",",
        "]"
      )},"author":"${v11JsonEscape(author)}"}"""
  }

  /**
   *  Persists a `count` measure definition (no measured columns, like the agent's RecordCount) with a REST checkpoint
   *  written by `author`; the definition is created by that checkpoint, so its `created_by` is `author`.
   */
  private def v11CreateCountCheckpointViaRest(partitioningId: Long, author: String): Unit = {
    val body =
      s"""{"id":"${java.util.UUID.randomUUID()}","name":"v11ParentCheckpoint","author":"${v11JsonEscape(author)}",""" +
        """"measuredByAtumAgent":false,"processStartTime":"2026-01-01T00:00:00Z","processEndTime":"2026-01-01T00:00:01Z",""" +
        """"measurements":[{"measure":{"measureName":"count","measuredColumns":[]},""" +
        """"result":{"mainValue":{"value":"4","valueType":"Long"},"supportValues":{}}}]}"""
    val response = httpCall("POST", s"/api/v2/partitionings/$partitioningId/checkpoints", Some(body))
    assert(response.statusCode() == 201, s"unexpected checkpoint response: ${response.body()}")
  }

  private def v11PatchBody(parentId: Long): String =
    s"""{"parentPartitioningId":$parentId,"author":"compat","copyMeasurements":false,"copyAdditionalData":false}"""

  private def v11Decode(response: CompatResponse): PartitioningWithIdDTO =
    decode[SingleSuccessResponse[PartitioningWithIdDTO]](response.body()) match {
      case Right(parsed) => parsed.data
      case Left(error) => fail(s"cannot decode create-partitioning response: $error, body: ${response.body()}")
    }

  private def v11AncestorIds(partitioningId: Long): Seq[Long] = {
    val response = httpCall("GET", s"/api/v2/partitionings/$partitioningId/ancestors?limit=1000")
    assert(response.statusCode() == 200, s"unexpected response: ${response.body()}")
    decode[PaginatedResponse[PartitioningWithIdDTO]](response.body()) match {
      case Right(parsed) => parsed.data.map(_.id)
      case Left(error) => fail(s"cannot decode ancestors response: $error")
    }
  }

  /** Persists a `count` measure definition on the context (a definition is created with the first checkpoint). */
  private def v11CreateCheckpointWithCount(context: AtumContext): Unit = {
    val spark = SparkSession
      .builder()
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()
    val df = spark.createDataFrame(spark.sparkContext.parallelize(testDataForRDD), testDataSchema)

    context.addMeasure(RecordCount())
    context.createCheckpoint("v11CheckpointCount", df)
  }

  // Need to add service & pg run in CI
  test("v1.1: re-POST of an existing partitioning answers 200 with the same id and the stored author") {
    // VERIFY-01 (Phase 11)
    val key = ListMap("v11Repost" -> s"r$v11Run")

    val created = httpCall("POST", "/api/v2/partitionings", Some(v11PostBody(key, Seq.empty, "compat-author-1")))
    assert(created.statusCode() == 201, s"unexpected response: ${created.body()}")
    val createdLocation = created.headers().firstValue("Location").orElse("")
    val createdDto = v11Decode(created)
    assert(createdLocation.endsWith(s"/${createdDto.id}"), s"unexpected Location: $createdLocation")
    assert(createdDto.author == "compat-author-1")

    val existing = httpCall("POST", "/api/v2/partitionings", Some(v11PostBody(key, Seq.empty, "compat-author-2")))
    assert(existing.statusCode() == 200, s"unexpected response: ${existing.body()}")
    assert(existing.headers().firstValue("Location").orElse("") == createdLocation)
    val existingDto = v11Decode(existing)
    assert(existingDto.id == createdDto.id)
    assert(existingDto.author == "compat-author-1", "the 200 carries the stored author, not the one of the request")

    assert(v11PartitioningIds(key) == Seq(createdDto.id), "exactly one partitioning row for the key")
  }

  // Need to add service & pg run in CI
  test("v1.1: a single new parent is linked to an already existing child through the agent API") {
    // VERIFY-01 (Phase 11)
    val agent = v11Agent()
    val childMap = ListMap("v11LinkChild" -> s"c$v11Run")
    val parentMap = ListMap("v11LinkParent" -> s"p$v11Run")

    val childRoot = agent.getOrCreateAtumContext(childMap)
    val parent = agent.getOrCreateAtumContext(parentMap)
    val childId = v11PartitioningIds(childMap).head
    val parentId = v11PartitioningIds(parentMap).head
    assert(v11AncestorIds(childId).isEmpty, "the existing child is a root before the link")

    val linkedChild = parent.subPartitionContext(childMap, mergeWithParent = false)
    assert(linkedChild.atumPartitions == childRoot.atumPartitions)

    assert(v11PartitioningIds(childMap) == Seq(childId), "still exactly one child row")
    assert(v11AncestorIds(childId) == Seq(parentId))
    assert(v11FlowsOf(childId).contains(v11PrimaryFlowId(parentId)), "the child is in the parent's primary flow")
  }

  // Need to add service & pg run in CI
  test("v1.1: copyMeasureDefinitions true copies only on a new child; false and the default copy nothing") {
    // VERIFY-01 (Phase 11)
    val agent = v11Agent()
    val parentMap = ListMap("v11CopyParent" -> s"p$v11Run")
    val parent = agent.getOrCreateAtumContext(parentMap)
    val parentId = v11PartitioningIds(parentMap).head
    // The parent's `count` definition is created by a REST checkpoint written by a DIFFERENT author than the agent
    // user, so that the later "created_by = caller" assertion cannot pass by accident (copy-from-source author).
    v11CreateCountCheckpointViaRest(parentId, v11ParentAuthor)
    parent.addAdditionalData("v11Key", "v11Value")

    val parentDefinitions = v11MeasureDefinitions(parentId)
    assert(parentDefinitions.map { case (name, columns, _) => (name, columns) } == Seq(("count", "{}")))
    assert(parentDefinitions.forall { case (_, _, createdBy) => createdBy == v11ParentAuthor })
    assert(v11AdditionalDataCount(parentId) == 1)
    val parentNameColumns = parentDefinitions.map { case (name, columns, _) => (name, columns) }

    // true + NEW child -> the definition is copied (created_by = caller), additional data is never copied
    val copiedMap = ListMap("v11CopyTrue" -> s"c$v11Run")
    parent.subPartitionContext(copiedMap, mergeWithParent = false, copyMeasureDefinitions = true)
    val copiedId = v11PartitioningIds(copiedMap).head
    val copiedDefinitions = v11MeasureDefinitions(copiedId)
    assert(copiedDefinitions.map { case (name, columns, _) => (name, columns) } == parentNameColumns)
    assert(
      copiedDefinitions.forall { case (_, _, createdBy) => createdBy == agent.currentUser },
      s"copied definitions must be created by the caller (${agent.currentUser}), not by the source author: $copiedDefinitions"
    )
    assert(v11AdditionalDataCount(copiedId) == 0, "additional data is never copied by POST")
    assert(v11AncestorIds(copiedId) == Seq(parentId))

    // false + NEW child -> nothing copied
    val falseMap = ListMap("v11CopyFalse" -> s"c$v11Run")
    parent.subPartitionContext(falseMap, mergeWithParent = false, copyMeasureDefinitions = false)
    val falseId = v11PartitioningIds(falseMap).head
    assert(v11MeasureDefinitions(falseId).isEmpty)
    assert(v11AncestorIds(falseId) == Seq(parentId))

    // default (2-arg overload) + NEW child -> nothing copied
    val defaultMap = ListMap("v11CopyDefault" -> s"c$v11Run")
    parent.subPartitionContext(defaultMap, mergeWithParent = false)
    val defaultId = v11PartitioningIds(defaultMap).head
    assert(v11MeasureDefinitions(defaultId).isEmpty)
    assert(v11AncestorIds(defaultId) == Seq(parentId))

    // true + ALREADY EXISTING child -> the link is created, nothing is copied (copy only on creation)
    val existingMap = ListMap("v11CopyExisting" -> s"c$v11Run")
    agent.getOrCreateAtumContext(existingMap)
    val existingId = v11PartitioningIds(existingMap).head
    parent.subPartitionContext(existingMap, mergeWithParent = false, copyMeasureDefinitions = true)
    assert(v11PartitioningIds(existingMap) == Seq(existingId))
    assert(v11MeasureDefinitions(existingId).isEmpty, "an existing child never receives copies")
    assert(v11AncestorIds(existingId) == Seq(parentId))

    // multi-parent + true: both parents have `count` -> exactly one copied `count` (distinct union)
    val secondParentMap = ListMap("v11CopyParent2" -> s"p$v11Run")
    val secondParent = agent.getOrCreateAtumContext(secondParentMap)
    v11CreateCheckpointWithCount(secondParent)
    val secondParentId = v11PartitioningIds(secondParentMap).head
    val multiMap = ListMap("v11CopyMulti" -> s"c$v11Run")
    agent.getOrCreateAtumSubContext(multiMap, Seq(parent, secondParent), copyMeasureDefinitions = true)
    val multiId = v11PartitioningIds(multiMap).head
    val multiDefinitions = v11MeasureDefinitions(multiId)
    assert(
      multiDefinitions.map { case (name, columns, _) => (name, columns) } == parentNameColumns,
      s"both parents hold `count` -> exactly one copied definition: $multiDefinitions"
    )
    assert(v11AncestorIds(multiId).toSet == Set(parentId, secondParentId))

    // multi-parent default (2-arg overload) -> nothing copied
    val multiDefaultMap = ListMap("v11CopyMultiDefault" -> s"c$v11Run")
    agent.getOrCreateAtumSubContext(multiDefaultMap, Seq(parent, secondParent))
    val multiDefaultId = v11PartitioningIds(multiDefaultMap).head
    assert(v11MeasureDefinitions(multiDefaultId).isEmpty)
    assert(v11AncestorIds(multiDefaultId).toSet == Set(parentId, secondParentId))
  }

  // Need to add service & pg run in CI
  test("v1.1: a dependency cycle is rejected with HttpException 409 and leaves the relationship unchanged") {
    // VERIFY-01 (Phase 11)
    val agent = v11Agent()
    val parentMap = ListMap("v11CycleParent" -> s"p$v11Run")
    val childMap = ListMap("v11CycleChild" -> s"c$v11Run")

    val parent = agent.getOrCreateAtumContext(parentMap)
    val child = parent.subPartitionContext(childMap, mergeWithParent = false)
    val parentId = v11PartitioningIds(parentMap).head
    val childId = v11PartitioningIds(childMap).head
    assert(v11AncestorIds(childId) == Seq(parentId))

    // making the parent a child of its own child would create a cycle
    val cycle = intercept[HttpException] {
      child.subPartitionContext(parentMap, mergeWithParent = false)
    }
    assert(cycle.statusCode == 409)

    // a self-link through the PATCH endpoint is a cycle as well
    val selfLink = httpCall("PATCH", s"/api/v2/partitionings/$parentId/ancestors", Some(v11PatchBody(parentId)))
    assert(selfLink.statusCode() == 409, s"unexpected response: ${selfLink.body()}")

    assert(v11AncestorIds(parentId).isEmpty, "the parent is still a root")
    assert(v11AncestorIds(childId) == Seq(parentId))
    assert(v11PartitioningIds(parentMap) == Seq(parentId))
    assert(v11PartitioningIds(childMap) == Seq(childId))
  }

  // Need to add service & pg run in CI
  test("v1.1: an unknown parent answers 404 and the child is not created") {
    // VERIFY-01 (Phase 11)
    val childMap = ListMap("v11UnknownParentChild" -> s"c$v11Run")
    assert(v11PartitioningIds(childMap).isEmpty)

    val response = httpCall("POST", "/api/v2/partitionings", Some(v11PostBody(childMap, Seq(v11UnknownId), "compat")))
    assert(response.statusCode() == 404, s"unexpected response: ${response.body()}")
    assert(v11PartitioningIds(childMap).isEmpty, "the child must not be created when a parent is unknown")

    // through the agent: a context of a partitioning that was never created cannot be used as a parent
    // (AtumContext's constructor is package-private to `za.co.absa.atum.agent`, which this test lives in).
    val agent = v11Agent()
    val ghostParent = new AtumContext(ListMap("v11GhostParent" -> s"g$v11Run"), agent)
    val agentChildMap = ListMap("v11UnknownParentAgentChild" -> s"c$v11Run")
    val notFound = intercept[HttpException] {
      ghostParent.subPartitionContext(agentChildMap, mergeWithParent = false)
    }
    assert(notFound.statusCode == 404)
    assert(v11PartitioningIds(agentChildMap).isEmpty, "the child must not be created when the parent lookup fails")
  }
}

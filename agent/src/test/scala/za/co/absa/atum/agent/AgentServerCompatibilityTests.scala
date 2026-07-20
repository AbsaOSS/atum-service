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

import scala.collection.immutable.ListMap

class AgentServerCompatibilityTests extends DBTestSuite {

  private val serverUrl = "http://localhost:8080"

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
   * Runs the full agent -> server flow for the given `agent` and `partitioning` and asserts that the
   * server persisted everything correctly.
   *
   * Every DB assertion is scoped to the partitioning created by this flow (via `fk_partitioning`), so
   * multiple compatibility tests can run against the same shared database without their writes
   * interfering with each other's assertions. Note: the agent writes go through the server on its own
   * DB connection and are therefore committed (not rolled back by balta between tests).
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

        assert(row.getJsonB("partitioning").contains(expectedPartitioning))
        assert(!partitioningsResult.hasNext)

        row.getLong("id_partitioning").get
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

        assert(!measureDefResult.hasNext)

        row.getLong("id_measure_definition").get
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
      "partition1" -> "valueFromTest1",
      "partition2" -> "valueFromTest2"
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
      case other             => fail(s"Expected an HttpDispatcher, but got ${other.getClass.getName}")
    }

    // The custom retry values must have been read from the config and must differ from the defaults,
    // confirming the non-default configuration is actually taking effect (not silently falling back).
    val retry = HttpRetry.fromConfig(customConfig)
    assert(retry.maxRetries == 5)
    assert(retry.initialDelay == 250L)
    assert(retry.maxDelay == 8000L)
    assert(retry != HttpRetry.Default)

    val domainAtumPartitioning = ListMap(
      "customPartition1" -> "customValueFromTest1",
      "customPartition2" -> "customValueFromTest2"
    )

    verifyAgentServerCompatibility(agent, domainAtumPartitioning)
  }
}

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
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}

import scala.collection.immutable.ListMap

class AgentWithServerIntegrationTests extends DBTestSuite {

  private val testDataForRDD = Seq(
    Row("A", 8.0),
    Row("B", 2.9),
    Row("C", 9.1),
    Row("D", 2.5)
  )

  private val testDataSchema = new StructType()
    .add(StructField("notImportantColumn", StringType))
    .add(StructField("columnForSum", DoubleType))

  // Need to add service & pg run in CI and perhaps make this a new test type - e2e
  ignore("Agent should be compatible with server") {

    val expectedMeasurement = JsonBString(
      """{"mainValue": {"value": "4", "valueType": "Long"}, "supportValues": {}}""".stripMargin
    )

    val expectedPartitioning = JsonBString(
      """{"keys": ["partition1", "partition2"], "version": 1, "keysToValuesMap": {"partition1": "valueFromTest1", "partition2": "valueFromTest2"}}""".stripMargin
    )

    // Test data for Checkpoint Calculation
    val spark = SparkSession
      .builder()
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    val rdd = spark.sparkContext.parallelize(testDataForRDD)
    val df = spark.createDataFrame(rdd, testDataSchema)

    // Atum Agent preparation - Agent configured to work with HTTP Dispatcher and service on localhost
    val agent = AtumAgent
    agent.dispatcherFromConfig(
      ConfigFactory
        .empty()
        .withValue("atum.dispatcher.type", ConfigValueFactory.fromAnyRef("http"))
        .withValue("atum.dispatcher.http.url", ConfigValueFactory.fromAnyRef("http://localhost:8080"))
    )

    // Atum Context stuff preparation - Partitioning, Measures, Additional Data, Checkpoint
    val domainAtumPartitioning = ListMap(
      "partition1" -> "valueFromTest1",
      "partition2" -> "valueFromTest2"
    )
    val domainAtumContext = agent.getOrCreateAtumContext(domainAtumPartitioning)

    domainAtumContext.addMeasure(RecordCount("*"))
    domainAtumContext.addAdditionalData("author", "Laco")
    domainAtumContext.addAdditionalData(Map("author" -> "LacoNew", "version" -> "1.0"))

    domainAtumContext.createCheckpoint("checkPointNameCount", df)

    // DB Check, data should be written in the DB
    table("runs.partitionings").all() { partitioningsResult =>
      assert(partitioningsResult.hasNext)
      val row = partitioningsResult.next()

      assert(row.getJsonB("partitioning").contains(expectedPartitioning))
      assert(!partitioningsResult.hasNext)
    }

    table("runs.additional_data").all() { adResult =>
      assert(adResult.hasNext)
      val row = adResult.next()

      assert(row.getString("ad_name").contains("author"))
      assert(row.getString("ad_value").contains("LacoNew"))

      assert(adResult.hasNext)
      val row2 = adResult.next()

      assert(row2.getString("ad_name").contains("version"))
      assert(row2.getString("ad_value").contains("1.0"))
      assert(!adResult.hasNext)
    }

    table("runs.additional_data_history").all() { adHistResult =>
      assert(adHistResult.hasNext)
      val row = adHistResult.next()

      assert(row.getString("ad_name").contains("author"))
      assert(row.getString("ad_value").contains("Laco"))

      assert(!adHistResult.hasNext)
    }

    table("runs.measure_definitions").all() { measureDefResult =>
      assert(measureDefResult.hasNext)
      val row = measureDefResult.next()

      assert(row.getString("measure_name").contains("*"))
      assert(row.getString("measured_columns").contains("{}"))

      assert(!measureDefResult.hasNext)
    }

    table("runs.measurements").all() { measurementsResult =>
      assert(measurementsResult.hasNext)
      val row = measurementsResult.next()

      assert(row.getJsonB("measurement_value").contains(expectedMeasurement))
      assert(!measurementsResult.hasNext)
    }

    // TODO Truncate data potentially, Balta might support this in the near future
  }
}

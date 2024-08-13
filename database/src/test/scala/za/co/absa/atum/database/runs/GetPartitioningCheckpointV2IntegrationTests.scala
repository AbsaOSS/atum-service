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

import java.time.OffsetDateTime
import java.util.UUID

class GetPartitioningCheckpointV2IntegrationTests extends DBTestSuite {

  private val fncGetPartitioningCheckpointV2 = "runs.get_partitioning_checkpoint_v2"

  case class MeasuredDetails(
    measureName: String,
    measureColumns: Seq[String],
    measurementValue: JsonBString
  )

  private val partitioning1 = JsonBString(
    """
      |{
      | "version": 1,
      |  "keys": ["keyX", "keyY", "keyZ"],
      |  "keysToValues": {
      |    "keyX": "value1",
      |    "keyZ": "value3",
      |    "keyY": "value2"
      |  }
      |}
      |""".stripMargin
  )

  private val partitioning2 = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3", "key2", "key4"],
      |  "keysToValues": {
      |    "key1": "valueX",
      |    "key2": "valueY",
      |    "key3": "valueZ",
      |    "key4": "valueA"
      |  }
      |}
      |""".stripMargin
  )

  private val measurement1 = JsonBString("""1""".stripMargin)

  private val measured_columns = CustomDBType("""{"col2"}""", "TEXT[]")

  test("Get partitioning checkpoints returns checkpoints for partitioning with checkpoints") {

    val uuid = UUID.randomUUID
    val startTime = OffsetDateTime.parse("1992-08-03T10:00:00Z")
    val endTime = OffsetDateTime.parse("2022-11-05T08:00:00Z")

    val id_measure_definition: Long = 1

    table("runs.partitionings").insert(
      add("partitioning", partitioning1)
        .add("created_by", "Daniel")
    )

    val fkPartitioning1: Long = table("runs.partitionings")
      .fieldValue("partitioning", partitioning1, "id_partitioning")
      .get
      .get

    table("runs.checkpoints").insert(
      add("id_checkpoint", uuid)
        .add("fk_partitioning", fkPartitioning1)
        .add("checkpoint_name", "checkpoint_1")
        .add("process_start_time", startTime)
        .add("process_end_time", endTime)
        .add("measured_by_atum_agent", true)
        .add("created_by", "Daniel")
    )

    table("runs.measure_definitions").insert(
      add("id_measure_definition", id_measure_definition)
        .add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Daniel")
        .add("measure_name", "measure_1")
        .add("measured_columns", measured_columns)
    )

    table("runs.measurements").insert(
      add("fk_checkpoint", uuid)
        .add("fk_measure_definition", id_measure_definition)
        .add("measurement_value", measurement1)
    )

    function(fncGetPartitioningCheckpointV2)
      .setParam("i_partitioning_id", fkPartitioning1)
      .setParam("i_checkpoint_id", uuid)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val results = queryResult.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("Ok"))
        assert(results.getString("checkpoint_name").contains("checkpoint_1"))
        assert(results.getUUID("id_checkpoint").contains(uuid))
        assert(results.getOffsetDateTime("checkpoint_start_time").contains(startTime))
        assert(results.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(results.getJsonB("measurement_value").contains(measurement1))
        assert(results.getString("measure_name").contains("measure_1"))
        assert(!queryResult.hasNext)
      }
  }

  test("Get partitioning checkpoints returns no checkpoints for partitioning without checkpoints") {

    table("runs.partitionings").insert(
      add("partitioning", partitioning2)
        .add("created_by", "Daniel")
    )

    val fkPartitioning2: Long = table("runs.partitionings")
      .fieldValue("partitioning", partitioning2, "id_partitioning")
      .get
      .get

    function(fncGetPartitioningCheckpointV2)
      .setParam("i_partitioning_id", fkPartitioning2)
      .setParam("i_checkpoint_id", UUID.randomUUID())
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val results = queryResult.next()
        assert(results.getInt("status").contains(42))
        assert(results.getString("status_text").contains("Checkpoint not found"))
      }

  }

  test("Get partitioning checkpoints no checkpoints non-existent partitionings") {

    function(fncGetPartitioningCheckpointV2)
      .setParam("i_partitioning_id", 0L)
      .setParam("i_checkpoint_id", UUID.randomUUID())
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val results = queryResult.next()
        assert(results.getInt("status").contains(41))
        assert(results.getString("status_text").contains("Partitioning not found"))
      }

  }

}

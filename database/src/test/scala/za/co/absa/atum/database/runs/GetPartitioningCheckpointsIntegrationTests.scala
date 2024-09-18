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

class GetPartitioningCheckpointsIntegrationTests extends DBTestSuite {

  private val fncGetPartitioningCheckpoints = "runs.get_partitioning_checkpoints"

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

  private val i_checkpoints_limit = 1
  private val i_offset = 0
  private val i_checkpoint_name = "checkpoint_1"

  private val measurement1 = JsonBString("""1""".stripMargin)
  private val measurement2 = JsonBString("""2""".stripMargin)

  private val measured_columns1 = CustomDBType("""{"col1"}""", "TEXT[]")
  private val measured_columns2 = CustomDBType("""{"col2"}""", "TEXT[]")

  private val id_measure_definition1: Long = 1
  private val id_measure_definition2: Long = 2

  private val uuid1 = UUID.fromString("d56fa5e2-79af-4a08-8b0c-6f83ff12cb2c")
  private val uuid2 = UUID.fromString("6e42d61e-5cfa-45c1-9d0d-e1f3120107da")
  private val startTime1 = OffsetDateTime.parse("1992-08-03T10:00:00Z")
  private val startTime2 = OffsetDateTime.parse("1993-08-03T10:00:00Z")
  private val endTime = OffsetDateTime.parse("2022-11-05T08:00:00Z")

  test("Returns expected results when there is two measurements for one checkpoint") {
    table("runs.partitionings").insert(
      add("partitioning", partitioning1)
        .add("created_by", "Daniel")
    )

    val fkPartitioning1: Long = table("runs.partitionings")
      .fieldValue("partitioning", partitioning1, "id_partitioning").get.get

    table("runs.checkpoints").insert(
      add("id_checkpoint", uuid1)
        .add("fk_partitioning", fkPartitioning1)
        .add("checkpoint_name", "checkpoint_1")
        .add("process_start_time", startTime1)
        .add("process_end_time", endTime)
        .add("measured_by_atum_agent", true)
        .add("created_by", "Daniel")
    )

    table("runs.measure_definitions").insert(
      add("id_measure_definition", id_measure_definition1)
        .add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Daniel")
        .add("measure_name", "measure_1")
        .add("measured_columns", measured_columns1)
    )

    table("runs.measure_definitions").insert(
      add("id_measure_definition", id_measure_definition2)
        .add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Daniel")
        .add("measure_name", "measure_2")
        .add("measured_columns", measured_columns2)
    )

    table("runs.measurements").insert(
      add("fk_checkpoint", uuid1)
        .add("fk_measure_definition", id_measure_definition1)
        .add("measurement_value", measurement1)
    )

    table("runs.measurements").insert(
      add("fk_checkpoint", uuid1)
        .add("fk_measure_definition", id_measure_definition2)
        .add("measurement_value", measurement2)
    )

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning_id", fkPartitioning1)
      .setParam("i_checkpoints_limit", i_checkpoints_limit)
      .setParam("i_offset", i_offset)
      .setParam("i_checkpoint_name", i_checkpoint_name)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val result1 = queryResult.next()
        assert(result1.getInt("status").contains(11))
        assert(result1.getString("status_text").contains("Ok"))
        assert(result1.getUUID("id_checkpoint").contains(uuid1))
        assert(result1.getString("checkpoint_name").contains("checkpoint_1"))
        assert(result1.getString("author").contains("Daniel"))
        assert(result1.getBoolean("measured_by_atum_agent").contains(true))
        assert(result1.getString("measure_name").contains("measure_1"))
        assert(result1.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col1")))
        assert(result1.getJsonB("measurement_value").contains(measurement1))
        assert(result1.getOffsetDateTime("checkpoint_start_time").contains(startTime1))
        assert(result1.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(result1.getBoolean("has_more").contains(false))

        assert(queryResult.hasNext)
        val result2 = queryResult.next()
        assert(result2.getInt("status").contains(11))
        assert(result2.getString("status_text").contains("Ok"))
        assert(result2.getUUID("id_checkpoint").contains(uuid1))
        assert(result2.getString("checkpoint_name").contains("checkpoint_1"))
        assert(result2.getString("author").contains("Daniel"))
        assert(result2.getBoolean("measured_by_atum_agent").contains(true))
        assert(result2.getString("measure_name").contains("measure_2"))
        assert(result2.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col2")))
        assert(result2.getJsonB("measurement_value").contains(measurement2))
        assert(result2.getOffsetDateTime("checkpoint_start_time").contains(startTime1))
        assert(result2.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(result2.getBoolean("has_more").contains(false))
        assert(!queryResult.hasNext)
      }
  }

  test(
    "Returns expected results when there is one measurement for each of two checkpoints and different filtration is applied"
  ) {
    table("runs.partitionings").insert(
      add("partitioning", partitioning1)
        .add("created_by", "Daniel")
    )

    val fkPartitioning1: Long = table("runs.partitionings")
      .fieldValue("partitioning", partitioning1, "id_partitioning").get.get

    table("runs.checkpoints").insert(
      add("id_checkpoint", uuid1)
        .add("fk_partitioning", fkPartitioning1)
        .add("checkpoint_name", "checkpoint_1")
        .add("process_start_time", startTime1)
        .add("process_end_time", endTime)
        .add("measured_by_atum_agent", true)
        .add("created_by", "Daniel")
    )

    table("runs.checkpoints").insert(
      add("id_checkpoint", uuid2)
        .add("fk_partitioning", fkPartitioning1)
        .add("checkpoint_name", "checkpoint_2")
        .add("process_start_time", startTime2)
        .add("process_end_time", endTime)
        .add("measured_by_atum_agent", true)
        .add("created_by", "Daniel")
    )

    table("runs.measure_definitions").insert(
      add("id_measure_definition", id_measure_definition1)
        .add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Daniel")
        .add("measure_name", "measure_1")
        .add("measured_columns", measured_columns1)
    )

    table("runs.measure_definitions").insert(
      add("id_measure_definition", id_measure_definition2)
        .add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Daniel")
        .add("measure_name", "measure_2")
        .add("measured_columns", measured_columns2)
    )

    table("runs.measurements").insert(
      add("fk_checkpoint", uuid1)
        .add("fk_measure_definition", id_measure_definition1)
        .add("measurement_value", measurement1)
    )

    table("runs.measurements").insert(
      add("fk_checkpoint", uuid2)
        .add("fk_measure_definition", id_measure_definition2)
        .add("measurement_value", measurement2)
    )

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning_id", fkPartitioning1)
      .setParam("i_checkpoints_limit", 2)
      .setParam("i_offset", i_offset)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val result1 = queryResult.next()
        assert(result1.getInt("status").contains(11))
        assert(result1.getString("status_text").contains("Ok"))
        assert(result1.getUUID("id_checkpoint").contains(uuid2))
        assert(result1.getString("checkpoint_name").contains("checkpoint_2"))
        assert(result1.getString("author").contains("Daniel"))
        assert(result1.getBoolean("measured_by_atum_agent").contains(true))
        assert(result1.getString("measure_name").contains("measure_2"))
        assert(result1.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col2")))
        assert(result1.getJsonB("measurement_value").contains(measurement2))
        assert(result1.getOffsetDateTime("checkpoint_start_time").contains(startTime2))
        assert(result1.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(result1.getBoolean("has_more").contains(false))

        val result2 = queryResult.next()
        assert(result2.getInt("status").contains(11))
        assert(result2.getString("status_text").contains("Ok"))
        assert(result2.getUUID("id_checkpoint").contains(uuid1))
        assert(result2.getString("checkpoint_name").contains("checkpoint_1"))
        assert(result2.getString("author").contains("Daniel"))
        assert(result2.getBoolean("measured_by_atum_agent").contains(true))
        assert(result2.getString("measure_name").contains("measure_1"))
        assert(result2.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col1")))
        assert(result2.getJsonB("measurement_value").contains(measurement1))
        assert(result2.getOffsetDateTime("checkpoint_start_time").contains(startTime1))
        assert(result2.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(result2.getBoolean("has_more").contains(false))
        assert(!queryResult.hasNext)
      }

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning_id", fkPartitioning1)
      .setParam("i_checkpoints_limit", 2)
      .setParam("i_offset", i_offset)
      .setParam("i_checkpoint_name", i_checkpoint_name)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val result1 = queryResult.next()
        assert(result1.getInt("status").contains(11))
        assert(result1.getString("status_text").contains("Ok"))
        assert(result1.getUUID("id_checkpoint").contains(uuid1))
        assert(result1.getString("checkpoint_name").contains("checkpoint_1"))
        assert(result1.getString("author").contains("Daniel"))
        assert(result1.getBoolean("measured_by_atum_agent").contains(true))
        assert(result1.getString("measure_name").contains("measure_1"))
        assert(result1.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col1")))
        assert(result1.getJsonB("measurement_value").contains(measurement1))
        assert(result1.getOffsetDateTime("checkpoint_start_time").contains(startTime1))
        assert(result1.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(result1.getBoolean("has_more").contains(false))

        assert(!queryResult.hasNext)
      }

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning_id", fkPartitioning1)
      .setParam("i_checkpoints_limit", 2)
      .setParam("i_offset", 1)
      .setParam("i_checkpoint_name", i_checkpoint_name)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val result = queryResult.next()
        assert(result.getInt("status").contains(12))
        assert(result.getString("status_text").contains("OK with no checkpoints found"))
        assert(!queryResult.hasNext)
      }

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning_id", 0L)
      .setParam("i_checkpoints_limit", 2)
      .setParam("i_offset", 1)
      .setParam("i_checkpoint_name", i_checkpoint_name)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val result = queryResult.next()
        assert(result.getInt("status").contains(41))
        assert(result.getString("status_text").contains("Partitioning not found"))
        assert(!queryResult.hasNext)
      }

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning_id", fkPartitioning1)
      .setParam("i_checkpoints_limit", 1)
      .setParam("i_offset", i_offset)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val result1 = queryResult.next()
        assert(result1.getInt("status").contains(11))
        assert(result1.getString("status_text").contains("Ok"))
        assert(result1.getUUID("id_checkpoint").contains(uuid2))
        assert(result1.getString("checkpoint_name").contains("checkpoint_2"))
        assert(result1.getString("author").contains("Daniel"))
        assert(result1.getBoolean("measured_by_atum_agent").contains(true))
        assert(result1.getString("measure_name").contains("measure_2"))
        assert(result1.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col2")))
        assert(result1.getJsonB("measurement_value").contains(measurement2))
        assert(result1.getOffsetDateTime("checkpoint_start_time").contains(startTime2))
        assert(result1.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(result1.getBoolean("has_more").contains(true))
        assert(!queryResult.hasNext)
      }
  }

  test("Returns expected status when partitioning not found"){
    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning_id", 1)
      .setParam("i_checkpoints_limit", i_checkpoints_limit)
      .setParam("i_offset", i_offset)
      .setParam("i_checkpoint_name", i_checkpoint_name)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val result1 = queryResult.next()
        assert(result1.getInt("status").contains(41))
        assert(result1.getString("status_text").contains("Partitioning not found"))
        assert(!queryResult.hasNext)
      }
  }

}

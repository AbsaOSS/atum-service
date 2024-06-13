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


class GetFlowCheckpointsIntegrationTests extends DBTestSuite {
  private val fncGetFlowCheckpoints = "flows.get_flow_checkpoints"

  case class MeasuredDetails (
    measureName: String,
    measureColumns: Seq[String],
    measurementValue: JsonBString
  )

  private val partitioning = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyX", "keyY", "keyZ"],
      |   "keysToValues": {
      |     "keyX": "value1",
      |     "keyZ": "value3",
      |     "keyY": "value2"
      |   }
      |}
      |""".stripMargin
  )

  private val measurementCnt = JsonBString(
    """
      |{
      |  "measure": {
      |    "measureName": "count",
      |    "measuredColumns": ["col1"]
      |  },
      |  "result": {
      |    "value": "3",
      |    "type": "int"
      |  }
      |}
      |""".stripMargin
  )

  private val measurementSum = JsonBString(
    """
      |{
      |  "measure": {
      |    "measureName": "sum",
      |    "measuredColumns": ["colOther"]
      |  },
      |  "result": {
      |    "value": "3000",
      |    "type": "int"
      |  }
      |}
      |""".stripMargin
  )

  private val measurementAvg = JsonBString(
    """
      |{
      |  "measure": {
      |    "measureName": "avg",
      |    "measuredColumns": ["a","b"]
      |  },
      |  "result": {
      |    "value": "2.71",
      |    "type": "double"
      |  }
      |}
      |""".stripMargin
  )

  test("Testing get_flow_checkpoints, partitioning and flow exist, but there are no checkpoints") {
    val partitioningId: Long = Random.nextLong()
    table("runs.partitionings").insert(
      add("id_partitioning", partitioningId)
        .add("partitioning", partitioning)
        .add("created_by", "Joseph")
    )

    val flowId: Long = Random.nextLong()
    table("flows.flows").insert(
      add("id_flow", flowId)
        .add("flow_name", "test_flow1")
        .add("flow_description", "Test Flow 1")
        .add("from_pattern", false)
        .add("created_by", "ObviouslySomeTest")
        .add("fk_primary_partitioning", partitioningId)
    )

    table("flows.partitioning_to_flow").insert(
      add("fk_flow", flowId)
        .add("fk_partitioning", partitioningId)
        .add("created_by", "ObviouslySomeTest")
    )

    function(fncGetFlowCheckpoints)
      .setParam("i_partitioning_of_flow", partitioning)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }
  }

  test("Testing get_flow_checkpoints, partitioning, flow and checkpoints all exist") {
    val partitioningId: Long = Random.nextLong()
    table("runs.partitionings").insert(
      add("id_partitioning", partitioningId)
        .add("partitioning", partitioning)
        .add("created_by", "Joseph")
    )

    val flowId: Long = Random.nextLong()
    table("flows.flows").insert(
      add("id_flow", flowId)
        .add("flow_name", "test_flow1")
        .add("flow_description", "Test Flow 1")
        .add("from_pattern", false)
        .add("created_by", "ObviouslySomeTest")
        .add("fk_primary_partitioning", partitioningId)
    )

    table("flows.partitioning_to_flow").insert(
      add("fk_flow", flowId)
        .add("fk_partitioning", partitioningId)
        .add("created_by", "ObviouslySomeTest")
    )

    val checkpointId = UUID.randomUUID
    val startTime = OffsetDateTime.parse("1993-02-14T10:00:00Z")
    val endTime = OffsetDateTime.parse("2024-04-24T10:00:00Z")
    table("runs.checkpoints").insert(
      add("id_checkpoint", checkpointId)
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "CheckpointNameCntAndAvg")
        .add("measured_by_atum_agent", true)
        .add("process_start_time", startTime)
        .add("process_end_time", endTime)
        .add("created_by", "ObviouslySomeTest")
    )

    val checkpointOtherId = UUID.randomUUID
    val startTimeOther = OffsetDateTime.parse("1993-02-14T10:00:00Z")
    val endTimeOther = OffsetDateTime.parse("2024-04-24T10:00:00Z")
    table("runs.checkpoints").insert(
      add("id_checkpoint", checkpointOtherId)
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "CheckpointNameOther")
        .add("measured_by_atum_agent", true)
        .add("process_start_time", startTimeOther)
        .add("process_end_time", endTimeOther)
        .add("created_by", "ObviouslySomeTest")
    )

    val measureDefinitionAvgId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", measureDefinitionAvgId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "avg")
        .add("measured_columns", CustomDBType("""{"a","b"}""", "TEXT[]"))
        .add("created_by", "ObviouslySomeTest")
    )

    val measureDefinitionCntId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", measureDefinitionCntId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "cnt")
        .add("measured_columns", CustomDBType("""{"col1"}""", "TEXT[]"))
        .add("created_by", "ObviouslySomeTest")
    )

    val measureDefinitionOtherId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", measureDefinitionOtherId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "sum")
        .add("measured_columns", CustomDBType("""{"colOther"}""", "TEXT[]"))
        .add("created_by", "ObviouslySomeTest")
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionCntId)
        .add("fk_checkpoint", checkpointId)
        .add("measurement_value", measurementCnt)
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionAvgId)
        .add("fk_checkpoint", checkpointId)
        .add("measurement_value", measurementAvg)
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionOtherId)
        .add("fk_checkpoint", checkpointOtherId)
        .add("measurement_value", measurementSum)
    )

    val actualMeasures: Seq[MeasuredDetails] = function(fncGetFlowCheckpoints)
      .setParam("i_partitioning_of_flow", partitioning)
      .setParam("i_checkpoint_name", "CheckpointNameCntAndAvg")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row1 = queryResult.next()
        assert(row1.getInt("status").contains(11))
        assert(row1.getString("status_text").contains("OK"))
        assert(row1.getUUID("id_checkpoint").contains(checkpointId))
        assert(row1.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row1.getOffsetDateTime("checkpoint_start_time").contains(startTime))
        assert(row1.getOffsetDateTime("checkpoint_end_time").contains(endTime))

        val measure1 = MeasuredDetails(
          row1.getString("measure_name").get,
          row1.getArray[String]("measured_columns").map(_.toList).get,
          row1.getJsonB("measurement_value").get
        )

        val row2 = queryResult.next()
        assert(row2.getInt("status").contains(11))
        assert(row2.getString("status_text").contains("OK"))
        assert(row2.getUUID("id_checkpoint").contains(checkpointId))
        assert(row2.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row2.getOffsetDateTime("checkpoint_start_time").contains(startTime))
        assert(row2.getOffsetDateTime("checkpoint_end_time").contains(endTime))

        val measure2 = MeasuredDetails(
          row2.getString("measure_name").get,
          row2.getArray[String]("measured_columns").map(_.toList).get,
          row2.getJsonB("measurement_value").get
        )

        assert(!queryResult.hasNext)
        Seq(measure1, measure2)
      }

    assert(actualMeasures.map(_.measureName).toSet == Set("avg", "cnt"))
    assert(actualMeasures.map(_.measureColumns).toSet == Set(Seq("col1"), Seq("a", "b")))
    actualMeasures.foreach { currVal =>
      val currValStr = currVal.measurementValue.value
      // Exact comparison is not trivial, we would need to deserialize (and potentially introduce some case classes
      // for this) and modify the JSON strings - not worth it, this should be enough as a sanity check.
      assert (currValStr.contains(""""value": "2.71"""") || currValStr.contains(""""value": "3""""))
    }
  }
}

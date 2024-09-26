package za.co.absa.atum.database.flows

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString
import za.co.absa.balta.classes.setter.CustomDBType

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Random

class GetFlowCheckpointsIntegrationTests extends DBTestSuite {
  private val fncGetFlowCheckpointsV2 = "flows.get_flow_checkpoints_v2"

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

  case class MeasuredDetails (
    measureName: String,
    measureColumns: Seq[String],
    measurementValue: JsonBString
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

  test("getFlowCheckpointsV2 should return all checkpoints for a given flow") {

    val partitioningId: Long = Random.nextLong()
    table("runs.partitionings").insert(
      add("id_partitioning", partitioningId)
        .add("partitioning", partitioning)
        .add("created_by", "Joseph")
    )

    val flowId: Long = Random.nextLong()
    table("flows.flows").insert(
      add("id_flow", flowId)
        .add("flow_name", "flowName")
        .add("from_pattern", false)
        .add("created_by", "Joseph")
        .add("fk_primary_partitioning", partitioningId)
    )

    table("flows.partitioning_to_flow").insert(
      add("fk_flow", flowId)
        .add("fk_partitioning", partitioningId)
        .add("created_by", "ObviouslySomeTest")
    )

    // Insert checkpoints and measure definitions
    val checkpointId1 = UUID.randomUUID()
    val startTime = OffsetDateTime.parse("1993-02-14T10:00:00Z")
    val endTime = OffsetDateTime.parse("2024-04-24T10:00:00Z")
    table("runs.checkpoints").insert(
      add("id_checkpoint", checkpointId1)
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "CheckpointNameCntAndAvg")
        .add("measured_by_atum_agent", true)
        .add("process_start_time", startTime)
        .add("process_end_time", endTime)
        .add("created_by", "Joseph")
    )

    val checkpointId2 = UUID.randomUUID()
    val startTimeOther = OffsetDateTime.parse("1993-02-14T10:00:00Z")
    val endTimeOther = OffsetDateTime.parse("2024-04-24T10:00:00Z")
    table("runs.checkpoints").insert(
      add("id_checkpoint", checkpointId2)
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "CheckpointNameOther")
        .add("measured_by_atum_agent", true)
        .add("process_start_time", startTimeOther)
        .add("process_end_time", endTimeOther)
        .add("created_by", "Joseph")
    )

    // Insert measure definitions and measurements
    val measureDefinitionAvgId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", measureDefinitionAvgId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "avg")
        .add("measured_columns", CustomDBType("""{"a","b"}""", "TEXT[]"))
        .add("created_by", "Joseph")
    )

    val measureDefinitionCntId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", measureDefinitionCntId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "cnt")
        .add("measured_columns", CustomDBType("""{"col1"}""", "TEXT[]"))
        .add("created_by", "Joseph")
    )

    val measureDefinitionOtherId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", measureDefinitionOtherId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "sum")
        .add("measured_columns", CustomDBType("""{"colOther"}""", "TEXT[]"))
        .add("created_by", "Joseph")
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionCntId)
        .add("fk_checkpoint", checkpointId1)
        .add("measurement_value", measurementCnt)
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionAvgId)
        .add("fk_checkpoint", checkpointId1)
        .add("measurement_value", measurementAvg)
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionOtherId)
        .add("fk_checkpoint", checkpointId2)
        .add("measurement_value", measurementSum)
    )

    // Actual test execution and assertions with limit and offset applied
    val actualMeasures: Seq[MeasuredDetails] = function(fncGetFlowCheckpointsV2)
      .setParam("i_flow_id", flowId)
      .execute("checkpoint_name") { queryResult =>
        assert(queryResult.hasNext)

        val row1 = queryResult.next()
        assert(row1.getInt("status").contains(11))
        assert(row1.getString("status_text").contains("OK"))
        assert(row1.getUUID("id_checkpoint").contains(checkpointId1))
        assert(row1.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row1.getString("author").contains("Joseph"))
        assert(row1.getBoolean("measured_by_atum_agent").contains(true))
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
        assert(row2.getUUID("id_checkpoint").contains(checkpointId1))
        assert(row2.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row2.getString("author").contains("Joseph"))
        assert(row2.getBoolean("measured_by_atum_agent").contains(true))
        assert(row2.getOffsetDateTime("checkpoint_start_time").contains(startTimeOther))
        assert(row2.getOffsetDateTime("checkpoint_end_time").contains(endTimeOther))

        val measure2 = MeasuredDetails(
          row2.getString("measure_name").get,
          row2.getArray[String]("measured_columns").map(_.toList).get,
          row2.getJsonB("measurement_value").get
        )

        assert(queryResult.hasNext)

        val row3 = queryResult.next()
        assert(row3.getInt("status").contains(11))
        assert(row3.getString("status_text").contains("OK"))
        assert(row3.getUUID("id_checkpoint").contains(checkpointId2))
        assert(row3.getString("checkpoint_name").contains("CheckpointNameOther"))
        assert(row3.getString("author").contains("Joseph"))
        assert(row3.getBoolean("measured_by_atum_agent").contains(true))
        assert(row3.getOffsetDateTime("checkpoint_start_time").contains(startTimeOther))
        assert(row3.getOffsetDateTime("checkpoint_end_time").contains(endTimeOther))

        val measure3 = MeasuredDetails(
          row3.getString("measure_name").get,
          row3.getArray[String]("measured_columns").map(_.toList).get,
          row3.getJsonB("measurement_value").get
        )

        Seq(measure1, measure2, measure3)
      }

    // Assertions for measures
    assert(actualMeasures.map(_.measureName).toSet == Set("avg", "cnt", "sum"))
    assert(actualMeasures.map(_.measureColumns).toSet == Set(List("a", "b"), List("col1"), List("colOther")))

    actualMeasures.foreach { currVal =>
      val currValStr = currVal.measurementValue.value

      currVal.measureName match {
        case "cnt" =>
          assert(currValStr.contains(""""value": "3""""))
        case "avg" =>
          assert(currValStr.contains(""""value": "2.71""""))
        case "sum" =>
          assert(currValStr.contains(""""value": "3000""""))
        case other =>
          fail(s"Unexpected measure name: $other")
      }
    }
  }

  test("getFlowCheckpointsV2 should return limited with checkpoints for a given flow") {

    val partitioningId: Long = Random.nextLong()
    table("runs.partitionings").insert(
      add("id_partitioning", partitioningId)
        .add("partitioning", partitioning)
        .add("created_by", "Joseph")
    )

    val flowId: Long = Random.nextLong()
    table("flows.flows").insert(
      add("id_flow", flowId)
        .add("flow_name", "flowName")
        .add("from_pattern", false)
        .add("created_by", "Joseph")
        .add("fk_primary_partitioning", partitioningId)
    )

    table("flows.partitioning_to_flow").insert(
      add("fk_flow", flowId)
        .add("fk_partitioning", partitioningId)
        .add("created_by", "ObviouslySomeTest")
    )

    // Insert checkpoints and measure definitions
    val checkpointId1 = UUID.randomUUID()
    val startTime = OffsetDateTime.parse("1993-02-14T10:00:00Z")
    val endTime = OffsetDateTime.parse("2024-04-24T10:00:00Z")
    table("runs.checkpoints").insert(
      add("id_checkpoint", checkpointId1)
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "CheckpointNameCntAndAvg")
        .add("measured_by_atum_agent", true)
        .add("process_start_time", startTime)
        .add("process_end_time", endTime)
        .add("created_by", "Joseph")
    )

    val checkpointId2 = UUID.randomUUID()
    val startTimeOther = OffsetDateTime.parse("1993-02-14T10:00:00Z")
    val endTimeOther = OffsetDateTime.parse("2024-04-24T10:00:00Z")
    table("runs.checkpoints").insert(
      add("id_checkpoint", checkpointId2)
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "CheckpointNameOther")
        .add("measured_by_atum_agent", true)
        .add("process_start_time", startTimeOther)
        .add("process_end_time", endTimeOther)
        .add("created_by", "Joseph")
    )

    // Insert measure definitions and measurements
    val measureDefinitionAvgId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", measureDefinitionAvgId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "avg")
        .add("measured_columns", CustomDBType("""{"a","b"}""", "TEXT[]"))
        .add("created_by", "Joseph")
    )

    val measureDefinitionCntId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", measureDefinitionCntId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "cnt")
        .add("measured_columns", CustomDBType("""{"col1"}""", "TEXT[]"))
        .add("created_by", "Joseph")
    )

    val measureDefinitionOtherId: Long = Random.nextLong()
    table("runs.measure_definitions").insert(
      add("id_measure_definition", measureDefinitionOtherId)
        .add("fk_partitioning", partitioningId)
        .add("measure_name", "sum")
        .add("measured_columns", CustomDBType("""{"colOther"}""", "TEXT[]"))
        .add("created_by", "Joseph")
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionCntId)
        .add("fk_checkpoint", checkpointId1)
        .add("measurement_value", measurementCnt)
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionAvgId)
        .add("fk_checkpoint", checkpointId1)
        .add("measurement_value", measurementAvg)
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionOtherId)
        .add("fk_checkpoint", checkpointId2)
        .add("measurement_value", measurementSum)
    )

    // Actual test execution and assertions
    val actualMeasures: Seq[MeasuredDetails] = function(fncGetFlowCheckpointsV2)
      .setParam("i_flow_id", flowId)
      .setParam("i_limit", 2)
      .setParam("i_offset", 0)
      .execute("checkpoint_name") { queryResult =>
        assert(queryResult.hasNext)
        val row1 = queryResult.next()
        assert(row1.getInt("status").contains(11))
        assert(row1.getString("status_text").contains("OK"))
        assert(row1.getUUID("id_checkpoint").contains(checkpointId1))
        assert(row1.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row1.getString("author").contains("Joseph"))
        assert(row1.getBoolean("measured_by_atum_agent").contains(true))
        assert(row1.getOffsetDateTime("checkpoint_start_time").contains(startTime))
        assert(row1.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(queryResult.hasNext)

        val measure1 = MeasuredDetails(
          row1.getString("measure_name").get,
          row1.getArray[String]("measured_columns").map(_.toList).get,
          row1.getJsonB("measurement_value").get
        )

        val row2 = queryResult.next()
        assert(row2.getInt("status").contains(11))
        assert(row2.getString("status_text").contains("OK"))
        assert(row2.getUUID("id_checkpoint").contains(checkpointId1))
        assert(row2.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row2.getString("author").contains("Joseph"))
        assert(row2.getBoolean("measured_by_atum_agent").contains(true))
        assert(row2.getOffsetDateTime("checkpoint_start_time").contains(startTimeOther))
        assert(row2.getOffsetDateTime("checkpoint_end_time").contains(endTimeOther))
        assert(queryResult.hasNext)

        val measure2 = MeasuredDetails(
          row2.getString("measure_name").get,
          row2.getArray[String]("measured_columns").map(_.toList).get,
          row2.getJsonB("measurement_value").get
        )

        val row3 = queryResult.next()
        assert(row3.getInt("status").contains(11))
        assert(row3.getString("status_text").contains("OK"))
        assert(row3.getUUID("id_checkpoint").contains(checkpointId2))
        assert(row3.getString("checkpoint_name").contains("CheckpointNameOther"))
        assert(row3.getString("author").contains("Joseph"))
        assert(row3.getBoolean("measured_by_atum_agent").contains(true))
        assert(row3.getOffsetDateTime("checkpoint_start_time").contains(startTimeOther))
        assert(row3.getOffsetDateTime("checkpoint_end_time").contains(endTimeOther))

        val measure3 = MeasuredDetails(
          row3.getString("measure_name").get,
          row3.getArray[String]("measured_columns").map(_.toList).get,
          row3.getJsonB("measurement_value").get
        )

        assert(!queryResult.hasNext)
        Seq(measure1, measure2, measure3)
      }

    // Assertions for measures
    assert(actualMeasures.map(_.measureName).toSet == Set("avg", "sum"))
    assert(actualMeasures.map(_.measureColumns).toSet == Set(List("a", "b"), List("colOther")))

    actualMeasures.foreach { currVal =>
      val currValStr = currVal.measurementValue.value

      currVal.measureName match {
        case "avg" =>
          assert(currValStr.contains(""""value": "2.71""""))
        case "cnt" =>
          assert(currValStr.contains(""""value": "3""""))
        case "sum" =>
          assert(currValStr.contains(""""value": "3000""""))
        case other =>
          fail(s"Unexpected measure name: $other")
      }
    }
  }

  test("getFlowCheckpointsV2 should return no flows when flow_id is not found") {

    // Create a non-existent flowId that doesn't exist in the database
    val nonExistentFlowId: Long = Random.nextLong()

    // Execute the function with the non-existent flowId
    val queryResult = function(fncGetFlowCheckpointsV2)
      .setParam("i_flow_id", nonExistentFlowId)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getString("status_text").contains("Flow not found"))
        assert(results.getInt("status").contains(42))
        assert(!queryResult.hasNext)
      }

  }

}

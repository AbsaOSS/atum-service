package za.co.absa.atum.database.flows

import io.circe.Json
import io.circe.parser.parse
import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString
import za.co.absa.balta.classes.setter.CustomDBType

import java.time.OffsetDateTime
import java.util.UUID
import scala.util.Random

class GetFlowCheckpointsIntegrationTests extends DBTestSuite {
  private val fncGetFlowCheckpointsV2 = "flows.get_flow_checkpoints"

  private val partitioning = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyX", "keyY", "keyZ"],
      |   "keysToValuesMap": {
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
        assert(row1.getString("checkpoint_author").contains("Joseph"))
        assert(row1.getBoolean("measured_by_atum_agent").contains(true))
        assert(row1.getOffsetDateTime("checkpoint_start_time").contains(startTime))
        assert(row1.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(row1.getBoolean("has_more").contains(false))

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
        assert(row2.getString("checkpoint_author").contains("Joseph"))
        assert(row2.getBoolean("measured_by_atum_agent").contains(true))
        assert(row2.getOffsetDateTime("checkpoint_start_time").contains(startTimeOther))
        assert(row2.getOffsetDateTime("checkpoint_end_time").contains(endTimeOther))
        assert(row1.getBoolean("has_more").contains(false))

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
        assert(row3.getString("checkpoint_author").contains("Joseph"))
        assert(row3.getBoolean("measured_by_atum_agent").contains(true))
        assert(row3.getOffsetDateTime("checkpoint_start_time").contains(startTimeOther))
        assert(row3.getOffsetDateTime("checkpoint_end_time").contains(endTimeOther))
        assert(row1.getBoolean("has_more").contains(false))

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

    function(fncGetFlowCheckpointsV2)
      .setParam("i_flow_id", flowId)
      .setParam("i_checkpoints_limit", 1)
      .execute("checkpoint_name") { queryResult =>
        assert(queryResult.hasNext)

        val row1 = queryResult.next()
        assert(row1.getBoolean("has_more").contains(true))

        val row2 = queryResult.next()
        assert(row2.getBoolean("has_more").contains(true))

        assert(!queryResult.hasNext)
      }

    function(fncGetFlowCheckpointsV2)
      .setParam("i_flow_id", flowId)
      .setParam("i_offset", 1)
      .execute("checkpoint_name") { queryResult =>
        assert(queryResult.hasNext)

        val row1 = queryResult.next()
        assert(row1.getBoolean("has_more").contains(false))

        assert(!queryResult.hasNext)
      }
  }

  test("getFlowCheckpointsV2 should return all checkpoints of a given name and a given flow") {

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
    val checkpointId1 = UUID.fromString("e9efe108-d8fc-42ad-b367-aa1b17f6c450")
    val startTime1 = OffsetDateTime.parse("1993-02-14T10:00:00Z")
    val endTime1 = OffsetDateTime.parse("2024-04-24T10:00:00Z")
    table("runs.checkpoints").insert(
      add("id_checkpoint", checkpointId1)
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "CheckpointNameCntAndAvg")
        .add("measured_by_atum_agent", true)
        .add("process_start_time", startTime1)
        .add("process_end_time", endTime1)
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

    val checkpointId3 = UUID.fromString("41ea35cd-6398-42ed-b6d3-a8d613176575")
    val startTime3 = OffsetDateTime.parse("1993-02-14T11:00:00Z")
    val endTime3 = OffsetDateTime.parse("2024-04-24T11:00:00Z")
    table("runs.checkpoints").insert(
      add("id_checkpoint", checkpointId3)
        .add("fk_partitioning", partitioningId)
        .add("checkpoint_name", "CheckpointNameCntAndAvg")
        .add("measured_by_atum_agent", true)
        .add("process_start_time", startTime3)
        .add("process_end_time", endTime3)
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
      add("fk_measure_definition", measureDefinitionCntId)
        .add("fk_checkpoint", checkpointId3)
        .add("measurement_value", measurementCnt)
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", measureDefinitionAvgId)
        .add("fk_checkpoint", checkpointId3)
        .add("measurement_value", measurementAvg)
    )

    table("runs.measurements").insert(
      add("fk_measure_definition", Random.nextLong())
        .add("fk_checkpoint", checkpointId2)
        .add("measurement_value", measurementSum)
    )

    // Actual test execution and assertions with limit and offset applied
    val actualMeasures: Seq[MeasuredDetails] = function(fncGetFlowCheckpointsV2)
      .setParam("i_flow_id", flowId)
      .setParam("i_checkpoint_name", "CheckpointNameCntAndAvg")
      .execute { queryResult =>
        assert(queryResult.hasNext)

        val row1 = queryResult.next()
        assert(row1.getInt("status").contains(11))
        assert(row1.getString("status_text").contains("OK"))
        assert(row1.getUUID("id_checkpoint").contains(checkpointId3))
        assert(row1.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row1.getString("checkpoint_author").contains("Joseph"))
        assert(row1.getBoolean("measured_by_atum_agent").contains(true))
        assert(row1.getOffsetDateTime("checkpoint_start_time").contains(startTime3))
        assert(row1.getOffsetDateTime("checkpoint_end_time").contains(endTime3))
        assert(row1.getLong("id_partitioning").contains(partitioningId))
        val expectedPartitioningJson1 = parseJsonBStringOrThrow(partitioning)
        val returnedPartitioningJson1 = parseJsonBStringOrThrow(row1.getJsonB("partitioning").get)
        assert(returnedPartitioningJson1 == expectedPartitioningJson1)
        assert(row1.getString("partitioning_author").contains("Joseph"))

        val measure1 = MeasuredDetails(
          row1.getString("measure_name").get,
          row1.getArray[String]("measured_columns").map(_.toList).get,
          row1.getJsonB("measurement_value").get
        )

        val row2 = queryResult.next()
        assert(row2.getInt("status").contains(11))
        assert(row2.getString("status_text").contains("OK"))
        assert(row2.getUUID("id_checkpoint").contains(checkpointId3))
        assert(row2.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row2.getString("checkpoint_author").contains("Joseph"))
        assert(row2.getBoolean("measured_by_atum_agent").contains(true))
        assert(row2.getOffsetDateTime("checkpoint_start_time").contains(startTime3))
        assert(row2.getOffsetDateTime("checkpoint_end_time").contains(endTime3))
        assert(row2.getLong("id_partitioning").contains(partitioningId))
        val expectedPartitioningJson2 = parseJsonBStringOrThrow(partitioning)
        val returnedPartitioningJson2 = parseJsonBStringOrThrow(row2.getJsonB("partitioning").get)
        assert(returnedPartitioningJson2 == expectedPartitioningJson2)
        assert(row2.getString("partitioning_author").contains("Joseph"))

        val measure2 = MeasuredDetails(
          row2.getString("measure_name").get,
          row2.getArray[String]("measured_columns").map(_.toList).get,
          row2.getJsonB("measurement_value").get
        )

        assert(queryResult.hasNext)

        val row3 = queryResult.next()
        assert(row3.getInt("status").contains(11))
        assert(row3.getString("status_text").contains("OK"))
        assert(row3.getUUID("id_checkpoint").contains(checkpointId1))
        assert(row3.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row3.getString("checkpoint_author").contains("Joseph"))
        assert(row3.getBoolean("measured_by_atum_agent").contains(true))
        assert(row3.getOffsetDateTime("checkpoint_start_time").contains(startTime1))
        assert(row3.getOffsetDateTime("checkpoint_end_time").contains(endTime1))
        assert(row3.getLong("id_partitioning").contains(partitioningId))
        val expectedPartitioningJson3 = parseJsonBStringOrThrow(partitioning)
        val returnedPartitioningJson3 = parseJsonBStringOrThrow(row2.getJsonB("partitioning").get)
        assert(returnedPartitioningJson3 == expectedPartitioningJson3)
        assert(row3.getString("partitioning_author").contains("Joseph"))

        val measure3 = MeasuredDetails(
          row3.getString("measure_name").get,
          row3.getArray[String]("measured_columns").map(_.toList).get,
          row3.getJsonB("measurement_value").get
        )

        val row4 = queryResult.next()
        assert(row4.getInt("status").contains(11))
        assert(row4.getString("status_text").contains("OK"))
        assert(row4.getUUID("id_checkpoint").contains(checkpointId1))
        assert(row4.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row4.getString("checkpoint_author").contains("Joseph"))
        assert(row4.getBoolean("measured_by_atum_agent").contains(true))
        assert(row4.getOffsetDateTime("checkpoint_start_time").contains(startTime1))
        assert(row4.getOffsetDateTime("checkpoint_end_time").contains(endTime1))
        assert(row4.getLong("id_partitioning").contains(partitioningId))
        val expectedPartitioningJson4 = parseJsonBStringOrThrow(partitioning)
        val returnedPartitioningJson4 = parseJsonBStringOrThrow(row2.getJsonB("partitioning").get)
        assert(returnedPartitioningJson4 == expectedPartitioningJson4)
        assert(row4.getString("partitioning_author").contains("Joseph"))

        val measure4 = MeasuredDetails(
          row4.getString("measure_name").get,
          row4.getArray[String]("measured_columns").map(_.toList).get,
          row4.getJsonB("measurement_value").get
        )

        Seq(measure1, measure2, measure3, measure4)
      }

    // Assertions for measures
    assert(actualMeasures.map(_.measureName).toSet == Set("avg", "cnt"))
    assert(actualMeasures.map(_.measureColumns).toSet == Set(List("a", "b"), List("col1")))

    actualMeasures.foreach { currVal =>
      val currValStr = currVal.measurementValue.value

      currVal.measureName match {
        case "cnt" =>
          assert(currValStr.contains(""""value": "3""""))
        case "avg" =>
          assert(currValStr.contains(""""value": "2.71""""))
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
      .setParam("i_checkpoints_limit", 2)
      .setParam("i_offset", 0L)
      .execute("checkpoint_name") { queryResult =>
        assert(queryResult.hasNext)
        val row1 = queryResult.next()
        assert(row1.getInt("status").contains(11))
        assert(row1.getString("status_text").contains("OK"))
        assert(row1.getUUID("id_checkpoint").contains(checkpointId1))
        assert(row1.getString("checkpoint_name").contains("CheckpointNameCntAndAvg"))
        assert(row1.getString("checkpoint_author").contains("Joseph"))
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
        assert(row2.getString("checkpoint_author").contains("Joseph"))
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
        assert(row3.getString("checkpoint_author").contains("Joseph"))
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
    function(fncGetFlowCheckpointsV2)
      .setParam("i_flow_id", nonExistentFlowId)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getString("status_text").contains("Flow not found"))
        assert(results.getInt("status").contains(42))
        assert(!queryResult.hasNext)
      }

  }

  private def parseJsonBStringOrThrow(jsonBString: JsonBString): Json = {
    parse(jsonBString.value).getOrElse(throw new Exception("Failed to parse JsonBString to Json"))
  }

}

package za.co.absa.atum.database.runs

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString
import za.co.absa.balta.classes.setter.CustomDBType

import java.time.OffsetDateTime
import java.util.UUID

class GetPartitioningCheckpointsIntegrationTests extends DBTestSuite{

  private val fncGetPartitioningCheckpoints = "runs.get_partitioning_checkpoints"

  case class MeasuredDetails(
    measureName: String,
    measureColumns: Seq[String],
    measurementValue: JsonBString
  )

  private val partitioningJson =
    """
      {
        "version": 1,
        "keys": ["keyX", "keyY", "keyZ"],
        "keysToValues": {
          "keyX": "value1",
          "keyZ": "value3",
          "keyY": "value2"
        }
      }
    """
  private val partitioning1 = JsonBString(partitioningJson)
  private val partitioning2 = JsonBString(
    """
        {
          "version": 1,
          "keys": ["key1", "key3", "key2", "key4"],
          "keysToValues": {
            "key1": "valueX",
            "key2": "valueY",
            "key3": "valueZ",
            "key4": "valueA"
          }
        }
      """
  )

  private val i_limit = 10
  private val i_checkpoint_name = "checkpoint_1"

  private val measurement1 = JsonBString("""1""".stripMargin)
  private val measurement2 = JsonBString("""2""".stripMargin)

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
      .fieldValue("partitioning", partitioning1, "id_partitioning").get.get

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

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning", partitioning1)
      .setParam("i_limit", i_limit)
      .setParam("i_checkpoint_name", i_checkpoint_name)
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

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning", partitioning1)
      .setParam("i_limit", i_limit)
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
        assert(!queryResult.hasNext)
      }
  }

  test("Get partitioning checkpoints returns multiple checkpoints for partitioning with multiple checkpoints") {

    val uuid1 = UUID.randomUUID
    val startTime1 = OffsetDateTime.parse("1992-08-03T10:00:00Z")
    val endTime1 = OffsetDateTime.parse("2022-11-05T08:00:00Z")

    val uuid2 = UUID.randomUUID
    val startTime2 = OffsetDateTime.parse("1995-05-15T12:00:00Z")
    val endTime2 = OffsetDateTime.parse("2025-07-20T15:00:00Z")

    val id_measure_definition1: Long = 1
    val id_measure_definition2: Long = 2

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
        .add("process_end_time", endTime1)
        .add("measured_by_atum_agent", true)
        .add("created_by", "Daniel")
    )

    table("runs.checkpoints").insert(
      add("id_checkpoint", uuid2)
        .add("fk_partitioning", fkPartitioning1)
        .add("checkpoint_name", "checkpoint_2")
        .add("process_start_time", startTime2)
        .add("process_end_time", endTime2)
        .add("measured_by_atum_agent", true)
        .add("created_by", "Daniel")
    )

    table("runs.measure_definitions").insert(
      add("id_measure_definition", id_measure_definition1)
        .add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Daniel")
        .add("measure_name", "measure_1")
        .add("measured_columns", measured_columns)
    )

    table("runs.measure_definitions").insert(
      add("id_measure_definition", id_measure_definition2)
        .add("fk_partitioning", fkPartitioning1)
        .add("created_by", "Daniel")
        .add("measure_name", "measure_2")
        .add("measured_columns", measured_columns)
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
      .setParam("i_partitioning", partitioning1)
      .setParam("i_limit", i_limit)
      .execute { queryResult =>
        assert(queryResult.hasNext)

        // Check the first result
        val results1 = queryResult.next()
        assert(results1.getInt("status").contains(11))
        assert(results1.getString("status_text").contains("Ok"))
        assert(results1.getString("checkpoint_name").contains("checkpoint_1"))
        assert(results1.getUUID("id_checkpoint").contains(uuid1))
        assert(results1.getOffsetDateTime("checkpoint_start_time").contains(startTime1))
        assert(results1.getOffsetDateTime("checkpoint_end_time").contains(endTime1))
        assert(results1.getJsonB("measurement_value").contains(measurement1))
        assert(results1.getString("measure_name").contains("measure_1"))

        // Check the second result
        assert(queryResult.hasNext)
        val results2 = queryResult.next()
        assert(results2.getInt("status").contains(11))
        assert(results2.getString("status_text").contains("Ok"))
        assert(results2.getString("checkpoint_name").contains("checkpoint_2"))
        assert(results2.getUUID("id_checkpoint").contains(uuid2))
        assert(results2.getOffsetDateTime("checkpoint_start_time").contains(startTime2))
        assert(results2.getOffsetDateTime("checkpoint_end_time").contains(endTime2))
        assert(results2.getJsonB("measurement_value").contains(measurement2))
        assert(results2.getString("measure_name").contains("measure_2"))

        assert(!queryResult.hasNext)
      }
  }

  test("Get partitioning checkpoints returns no checkpoints for partitioning without checkpoints") {

    table("runs.partitionings").insert(
      add("partitioning", partitioning2)
        .add("created_by", "Daniel")
    )

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning", partitioning2)
      .setParam("i_limit", i_limit)
      .setParam("i_checkpoint_name", i_checkpoint_name)
      .execute { queryResult =>
        assert(!queryResult.hasNext)
      }

  }

}

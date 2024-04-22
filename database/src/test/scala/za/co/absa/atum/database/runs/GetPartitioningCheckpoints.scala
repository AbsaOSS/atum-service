package za.co.absa.atum.database.runs

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString

import java.time.OffsetDateTime
import java.util.UUID

class GetPartitioningCheckpoints extends DBTestSuite{

  private val fncGetPartitioningCheckpoints = "runs.get_partitioning_checkpoints"

  private val partitioning1 = JsonBString(
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

  private val i_limit = 10
  private val i_checkpoint_name = "checkpoint_1"

  private val measurement1 = JsonBString("""1""".stripMargin)

  test("Get partitioning checkpoints returns checkpoints for partitioning with checkpoints") {

    val uuid = UUID.randomUUID
    val startTime = OffsetDateTime.parse("1992-08-03T10:00:00Z")
    val endTime = OffsetDateTime.parse("2022-11-05T08:00:00Z")

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

    table("runs.measurements").insert(
      add("fk_checkpoint", uuid)
        .add("fk_measure_definition", 1)
        .add("measurement_value", measurement1)
    )

    function(fncGetPartitioningCheckpoints)
      .setParam("i_partitioning", partitioning1)
      .setParam("i_limit", i_limit)
      .setParam("i_checkpoint_name", i_checkpoint_name)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("OK"))
        assert(results.getString("checkpoint_name").contains("checkpoint_1"))
        assert(results.getUUID("id_checkpoint").contains(uuid))
        assert(results.getOffsetDateTime("checkpoint_start_time").contains(startTime))
        assert(results.getOffsetDateTime("checkpoint_end_time").contains(endTime))
        assert(results.getBoolean("measured_by_atum_agent").contains(true))
        assert(results.getString("created_by").contains("Daniel"))
        assert(results.getInt("measurement_value").contains(1))
        assert(results.getString("measurement_name").contains("measurement_1"))
        assert(!queryResult.hasNext)
      }

  }
}

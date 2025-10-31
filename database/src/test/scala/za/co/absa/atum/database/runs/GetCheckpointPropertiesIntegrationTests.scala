package za.co.absa.atum.database.runs

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString

import java.time.OffsetDateTime
import java.util.UUID

class GetCheckpointPropertiesIntegrationTests extends DBTestSuite {

  private val fnGetCheckpointProperties = "runs.get_checkpoint_properties"

  test("Get properties for a checkpoint") {
    val uuid = UUID.randomUUID
    val user = "Alan Turing"
    val startTime = OffsetDateTime.parse("2000-01-01T00:00:00Z")
    val endTime = OffsetDateTime.parse("2000-01-01T01:00:00Z")

    // Insert partitioning
    table("runs.partitionings").insert(
      add("partitioning", JsonBString("""{"version":1,"keys":["k"],"keysToValuesMap":{"k":"v"}}"""))
        .add("created_by", user)
    )
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("created_by", user, "id_partitioning").get.get

    // Insert checkpoint
    table("runs.checkpoints").insert(
      add("id_checkpoint", uuid)
        .add("fk_partitioning", fkPartitioning)
        .add("checkpoint_name", "Test checkpoint")
        .add("process_start_time", startTime)
        .add("process_end_time", endTime)
        .add("measured_by_atum_agent", false)
        .add("created_by", user)
    )

    // Insert properties
    table("runs.checkpoint_properties").insert(
      add("fk_checkpoint", uuid)
        .add("property_name", "foo")
        .add("property_value", "bar")
    )
    table("runs.checkpoint_properties").insert(
      add("fk_checkpoint", uuid)
        .add("property_name", "baz")
        .add("property_value", "qux")
    )

    // Call the function
    function(fnGetCheckpointProperties)
      .setParam("i_checkpoint_id", uuid)
      .execute { queryResult =>
        import scala.collection.mutable.ListBuffer
        val statusCodes = ListBuffer.empty[Int]
        val statusTexts = ListBuffer.empty[String]
        val props = scala.collection.mutable.Map.empty[String, String]

        while (queryResult.hasNext) {
          val row = queryResult.next()
          statusCodes += row.getInt("status").get
          statusTexts += row.getString("status_text").get
          props += row.getString("property_name").get -> row.getString("property_value").get
        }

        assert(statusCodes.nonEmpty)
        assert(statusCodes.forall(_ == 11))
        assert(statusTexts.forall(_ == "OK"))
        assert(props == Map("foo" -> "bar", "baz" -> "qux") || props == Map("baz" -> "qux", "foo" -> "bar"))
      }

  }

  test("Get properties for a checkpoint with no properties") {
    val uuid = UUID.randomUUID
    val user = "Ada Lovelace"
    val startTime = OffsetDateTime.parse("2000-01-01T00:00:00Z")
    val endTime = OffsetDateTime.parse("2000-01-01T01:00:00Z")

    table("runs.partitionings").insert(
      add("partitioning", JsonBString("""{"version":1,"keys":["k"],"keysToValuesMap":{"k":"v"}}"""))
        .add("created_by", user)
    )
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("created_by", user, "id_partitioning").get.get

    table("runs.checkpoints").insert(
      add("id_checkpoint", uuid)
        .add("fk_partitioning", fkPartitioning)
        .add("checkpoint_name", "No props checkpoint")
        .add("process_start_time", startTime)
        .add("process_end_time", endTime)
        .add("measured_by_atum_agent", false)
        .add("created_by", user)
    )

    function(fnGetCheckpointProperties)
      .setParam("i_checkpoint_id", uuid)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").get == 11)
        assert(row.getString("status_text").get == "OK")
        assert(row.getString("property_name").isEmpty)
        assert(row.getString("property_value").isEmpty)
        assert(!queryResult.hasNext)
      }
  }

  test("Get properties for a non-existing checkpoint") {
    val nonExistingUuid = UUID.randomUUID

    function(fnGetCheckpointProperties)
      .setParam("i_checkpoint_id", nonExistingUuid)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").get == 42)
        assert(row.getString("status_text").get == "Checkpoint not found")
        assert(row.getString("property_name").isEmpty)
        assert(row.getString("property_value").isEmpty)
        assert(!queryResult.hasNext)
      }
  }

}

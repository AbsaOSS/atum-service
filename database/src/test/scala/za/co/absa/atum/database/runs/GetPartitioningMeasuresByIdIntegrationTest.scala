package za.co.absa.atum.database.runs

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString
import za.co.absa.balta.classes.setter.CustomDBType

class GetPartitioningMeasuresByIdIntegrationTest extends DBTestSuite {
  private val fncGetPartitioningMeasuresById = "runs.get_partitioning_measures_by_id"

  test("Get partitioning measures by id should return partitioning measures for partitioning with measures") {
    val partitioning = JsonBString(
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

    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", "Thomas")
    )

    val fkPartitioning: Long = table("runs.partitionings")
      .fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.measure_definitions").insert(
      add("fk_partitioning", fkPartitioning)
        .add("created_by", "Thomas")
        .add("measure_name", "measure1")
        .add("measured_columns", CustomDBType("""{"col1"}""", "TEXT[]"))
    )

    table("runs.measure_definitions").insert(
      add("fk_partitioning", fkPartitioning)
        .add("created_by", "Thomas")
        .add("measure_name", "measure2")
        .add("measured_columns", CustomDBType("""{"col2"}""", "TEXT[]"))
    )

    function(fncGetPartitioningMeasuresById)
      .setParam("i_partitioning", fkPartitioning)
      .execute { queryResult =>
        val results = queryResult.next()
        assert(results.getInt("status").contains(11))
        assert(results.getString("status_text").contains("OK"))
        assert(results.getString("measure_name").contains("measure1"))
        assert(results.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col1")))

        val results2 = queryResult.next()
        assert(results2.getInt("status").contains(11))
        assert(results2.getString("status_text").contains("OK"))
        assert(results2.getString("measure_name").contains("measure2"))
        assert(results2.getArray[String]("measured_columns").map(_.toSeq).contains(Seq("col2")))
      }
  }
}

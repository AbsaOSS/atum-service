package za.co.absa.atum.database.runs

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString

class GetPartitioningAditionalDataTest extends DBTestSuite{

  private val fncGetPartitioningAdditionalData = "runs.get_partitioning_additional_data"

  private val partitioning1 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["key1", "key2", "key3"],
      |   "keysToValues": {
      |     "key1": "value1",
      |     "key2": "value2",
      |     "key3": "value3"
      |   },
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

  test("Get partitioning additional data returns additional data for partitioning with additional data") {
    table("runs.partitionings").insert(
      add("partitioning", partitioning1)
        .add("created_by", "Thomas")
    )



  }

}

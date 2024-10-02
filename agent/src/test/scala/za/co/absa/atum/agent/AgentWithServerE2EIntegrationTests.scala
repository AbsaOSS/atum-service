package za.co.absa.atum.agent

import org.apache.spark.sql.types.{DoubleType, StringType, StructField, StructType}
import org.apache.spark.sql.{Row, SparkSession}
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.model.AtumMeasure.RecordCount
import za.co.absa.balta.DBTestSuite

import scala.collection.immutable.ListMap

class AgentWithServerE2EIntegrationTests extends DBTestSuite with Matchers {

  private val testDataForRDD = Seq(
    Row("A", 8.0),
    Row("B", 2.9),
    Row("C", 9.1),
    Row("D", 2.5)
  )

  private val testDataSchema = new StructType()
    .add(StructField("notImportantColumn", StringType))
    .add(StructField("columnForSum", DoubleType))


  test("Agent should be compatible with server") {
    val spark = SparkSession.builder()
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    val rdd = spark.sparkContext.parallelize(testDataForRDD)

    val domainAtumPartitioning = ListMap(
      "partition1" -> "valueFromTest1",
      "partition2" -> "valueFromTest2"
    )
    val domainAtumContext = AtumAgent.getOrCreateAtumContext(domainAtumPartitioning)

    println(domainAtumContext.currentMeasures)
    println(domainAtumContext.currentAdditionalData)

    domainAtumContext.addMeasure(RecordCount("*"))
    println(domainAtumContext.currentMeasures)

    domainAtumContext.addAdditionalData("author", "Laco")
    println(domainAtumContext.currentAdditionalData)

    domainAtumContext.addAdditionalData(Map("author" -> "LacoNew", "version" -> "1.0"))
    println(domainAtumContext.currentAdditionalData)

    val df = spark.createDataFrame(rdd, testDataSchema)

    domainAtumContext.createCheckpoint("checkPointNameCount", df)

  }
}

package pipelines

import org.apache.spark.sql.SparkSession
import za.co.absa.atum.agent.AtumContext
import za.co.absa.atum.agent.model._

import java.time.LocalDateTime

object Pipeline2 extends App with AtumContext {

  println("Running...")

  val spark = SparkSession
    .builder()
    .master("local[1]")
    .appName("The test")
    .getOrCreate()

  import spark.implicits._

  println("Spark Version : " + spark.version)

  val dfPerson = spark.read
    .format("csv")
    .option("header", "true")
    .load("agent/src/test/resources/random-dataset/persons.csv")

  val dsPerson = dfPerson.as[Person]

  dsPerson.write.save(s"out/${LocalDateTime.now()}")

  val dsEnrichment = spark.read
    .format("csv")
    .option("header", "true")
    .load("agent/src/test/resources/random-dataset/persons-enriched.csv")
    .as[Enrichment]

  val dfFull = dsPerson.join(dsEnrichment, Seq("id"))


  val measurementRecordCount = RecordCount("name", "id")
  val measurementAggregateTotal = AbsSumOfValuesOfColumn("name2", "salary")

  dfFull.addMeasure(measurementRecordCount)
  dfFull.addMeasure(Seq(measurementRecordCount, measurementAggregateTotal))

  dfFull.write.save(s"out/full/${LocalDateTime.now()}")

}

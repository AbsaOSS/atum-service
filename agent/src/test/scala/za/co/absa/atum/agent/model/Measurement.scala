package za.co.absa.atum.agent.model

import org.apache.spark.sql.{SQLContext, SQLImplicits, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import za.co.absa.atum.agent.AtumContext
import za.co.absa.atum.agent.AtumContext.DatasetWrapper
import za.co.absa.atum.agent.model.Measurement._

class MeasurementSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with BeforeAndAfterAll { self =>

  @transient var ss: SparkSession = null
  @transient var sc: SparkContext = null

  private object testImplicits extends SQLImplicits {
    protected override def _sqlContext: SQLContext = self.ss.sqlContext
  }

  override def beforeAll(): Unit = {
    val sparkConfig = new SparkConf()
    sparkConfig.set("spark.broadcast.compress", "false")
    sparkConfig.set("spark.shuffle.compress", "false")
    sparkConfig.set("spark.shuffle.spill.compress", "false")
    sparkConfig.set("spark.master", "local")

    ss = SparkSession.builder().config(sparkConfig).getOrCreate()

  }

  override def afterAll(): Unit = ss.stop()

  "Measurement" should "measures based on the dataframe" in {

    // Measures
    val measureIds: Measurement = RecordCount(MockMeasureNames.recordCount1, controlCol = "id")
    val salaryAbsSum: Measurement = AbsSumOfValuesOfColumn(
      MockMeasureNames.absSumOfValuesOfSalary,
      controlCol = "salary"
    )
    val salarySum = SumOfValuesOfColumn(MockMeasureNames.absSumOfValuesOfSalary, controlCol = "salary")
    val sumOfHashes: Measurement = SumOfHashesOfColumn(MockMeasureNames.hashSumOfNames, controlCol = "id")

    // AtumContext contains `Measurement`
    val atumContextInstanceWithRecordCount = AtumContext()
      .withMeasureAddedOrOverwritten(measureIds)
    val atumContextWithSalaryAbsMeasure = atumContextInstanceWithRecordCount
      .withMeasureAddedOrOverwritten(salaryAbsSum)
    val atumContextWithNameHashSum = AtumContext()
      .withMeasureAddedOrOverwritten(sumOfHashes)

    // Pipeline
    val dfPersons = ss.read
      .format("csv")
      .option("header", "true")
      .load("agent/src/test/resources/random-dataset/persons.csv")
      .createCheckpoint("name1", atumContextInstanceWithRecordCount)
      .createCheckpoint("name2", atumContextWithNameHashSum)

    val dsEnrichment = ss.read
      .format("csv")
      .option("header", "true")
      .load("agent/src/test/resources/random-dataset/persons-enriched.csv")
      .createCheckpoint("name3",
                        atumContextWithSalaryAbsMeasure.withMeasureRemoved(
                          MockMeasureNames.absSumOfValuesOfSalary
                        )
      )

    val dfFull = dfPersons
      .join(dsEnrichment, Seq("id"))
      .createCheckpoint("other different name", atumContextWithSalaryAbsMeasure)

    val dfExtraPersonWithNegativeSalary = ss
      .createDataFrame(
        Seq(
          ("id", "firstName", "lastName", "email", "email2", "profession", "-1000")
        )
      )
      .toDF("id", "firstName", "lastName", "email", "email2", "profession", "salary")

    val dfExtraPerson = dfExtraPersonWithNegativeSalary.union(dfPersons)

    dfExtraPerson.createCheckpoint(
      "a checkpoint name",
      atumContextWithSalaryAbsMeasure
        .withMeasureRemoved(MockMeasureNames.recordCount1)
        .withMeasureRemoved(MockMeasureNames.absSumOfValuesOfSalary)
    )

    // Assertions
    assert(measureIds.measurementFunction(dfPersons) == "1000")
    assert(measureIds.measurementFunction(dfFull) == "1000")
    assert(salaryAbsSum.measurementFunction(dfFull) == "2987144")
    assert(sumOfHashes.measurementFunction(dfFull) == "2044144307532")
    assert(salarySum.measurementFunction(dfExtraPerson) == "2986144")
    assert(salarySum.measurementFunction(dfFull) == "2987144")

  }

}

object MockMeasureNames {
  val recordCount1 = "record count"
  val absSumOfValuesOfSalary = "salary abs sum"
  val sumOfValuesOfSalary = "salary sum"
  val hashSumOfNames = "name hash sum"
}

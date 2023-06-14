package za.co.absa.atum.agent

import org.apache.spark.sql.{SQLContext, SQLImplicits, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import za.co.absa.atum.agent.AtumContext.DatasetWrapper
import za.co.absa.atum.agent.core.MeasurementProcessorImplementation
import za.co.absa.atum.agent.model._

class AtumContextSpec
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll { self =>

  @transient var ss: SparkSession = null
  @transient var sc: SparkContext = null

  @transient implicit val agent: AtumAgent = null
  @transient val processor: MeasurementProcessorImplementation =
    new MeasurementProcessorImplementation

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

  override def afterAll(): Unit = {
    ss.stop()
  }

  "setCheckpoint method" should "measures based on the dataframe" in {

    implicit val agent: AtumAgent = new MockAtumAgentImplAndUnitTest

    val atumContextInstanceWithRecordCount = AtumContext(processor = processor)
      .withMeasureAddedOrOverwritten(
        RecordCount(MockMeasureNames.recordCount1, controlCol = "id")
      )

    val atumContextWithSalaryAbsMeasure = atumContextInstanceWithRecordCount
      .withMeasureAddedOrOverwritten(
        AbsSumOfValuesOfColumn(
          MockMeasureNames.absSumOfValuesOfSalary,
          controlCol = "salary"
        )
      )

    val atumContextWithNameHashSum = AtumContext(processor = processor)
      .withMeasureAddedOrOverwritten(
        SumOfHashesOfColumn(MockMeasureNames.hashSumOfNames, controlCol = "id")
      )

    val dfPersons = ss.read
      .format("csv")
      .option("header", "true")
      .load("agent/src/test/resources/random-dataset/persons.csv")
      .setCheckpoint(atumContextInstanceWithRecordCount)
      .setCheckpoint(atumContextWithNameHashSum)

    val dsEnrichment = ss.read
      .format("csv")
      .option("header", "true")
      .load("agent/src/test/resources/random-dataset/persons-enriched.csv")
      .setCheckpoint(
        atumContextWithSalaryAbsMeasure.withMeasureRemoved(
          MockMeasureNames.absSumOfValuesOfSalary
        )
      )

    val dfFull = dfPersons
      .join(dsEnrichment, Seq("id"))
      .setCheckpoint(atumContextWithSalaryAbsMeasure)

    println(dfFull.count)

    val dfExtraPersonWithNegativeSalary = ss
      .createDataFrame(
        Seq(
          (
            "id",
            "firstName",
            "lastName",
            "email",
            "email2",
            "profession",
            "-1000"
          )
        )
      )
      .toDF(
        "id",
        "firstName",
        "lastName",
        "email",
        "email2",
        "profession",
        "salary"
      )

    val dfExtraPersons = dfExtraPersonWithNegativeSalary.union(dfPersons)

    dfExtraPersons.setCheckpoint(
      atumContextWithSalaryAbsMeasure
        .withMeasureRemoved(MockMeasureNames.recordCount1)
        .withMeasureRemoved(MockMeasureNames.absSumOfValuesOfSalary)
    )

    println(dfExtraPersons.count())
  }

  "withMeasureAddedOrOverwritten" should "add a new measure if not exists, overwrite it otherwise" in {

    implicit val atumAgent: MockAtumAgentImplAndUnitTest =
      new MockAtumAgentImplAndUnitTest
    val atumContext = AtumContext.context

    assert(atumContext.measurements.isEmpty)

    val atumContextWithRecordCount =
      atumContext.withMeasureAddedOrOverwritten(RecordCount("name 1", "id"))
    assert(atumContextWithRecordCount.measurements.size == 1)

    val atumContextWithTwoRecordCount =
      atumContextWithRecordCount.withMeasureAddedOrOverwritten(
        Seq(RecordCount("same name", "id"), RecordCount("same name", "x"))
      )
    assert(atumContextWithTwoRecordCount.measurements.size == 2)

    val atumContextWithTwoDistinctRecordCount =
      atumContextWithRecordCount.withMeasureAddedOrOverwritten(
        Seq(RecordCount("name 1", "id"), RecordCount("name 2", "one"))
      )
    assert(atumContextWithTwoDistinctRecordCount.measurements.size == 2)

    val editedAtumContextWithTwoDistinctRecordCount =
      atumContextWithTwoDistinctRecordCount.withMeasureAddedOrOverwritten(
        RecordCount("name 1", "changed")
      )
    assert(
      editedAtumContextWithTwoDistinctRecordCount
        .measurements("name 1")
        .controlCol == "changed"
    )
  }

  "withMeasureRemoved" should "remove a measure if exists" in {
    implicit val atumAgent: MockAtumAgentImplAndUnitTest =
      new MockAtumAgentImplAndUnitTest

    val atumContext = AtumContext.context
    assert(atumContext.measurements.isEmpty)

    val atumContext1 = atumContext.withMeasureAddedOrOverwritten(
      Seq(RecordCount("name 1", "id"), RecordCount("name 2", "id"))
    )
    assert(atumContext1.measurements.size == 2)

    val atumContextRemoved = atumContext1.withMeasureRemoved("name 1")
    assert(atumContextRemoved.measurements.size == 1)
    assert(atumContextRemoved.measurements.keys.head == "name 2")
  }

}

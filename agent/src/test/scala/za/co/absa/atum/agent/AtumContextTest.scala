/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.atum.agent

import org.apache.spark.sql.types.{DoubleType, StringType, StructField, StructType}
import org.apache.spark.sql.{Row, SparkSession}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.{mock, times, verify}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.AtumContext.AtumPartitions
import za.co.absa.atum.agent.model.Measure.{RecordCount, SumOfValuesOfColumn}
import za.co.absa.atum.agent.model.MeasurementProvided
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType
import za.co.absa.atum.model.dto._

class AtumContextTest extends AnyFlatSpec with Matchers {

  "withMeasureAddedOrOverwritten" should "add a new measure if not exists, overwrite it otherwise" in {

    val atumContext = AtumAgent.getOrCreateAtumContext(AtumPartitions("foo1"->"bar"))

    assert(atumContext.currentMeasures.isEmpty)

    val atumContextWithRecordCount =
      atumContext.addMeasure(RecordCount("id"))
    assert(atumContextWithRecordCount.currentMeasures.size == 1)

    val atumContextWithTwoRecordCount =
      atumContextWithRecordCount.addMeasures(
        Set(RecordCount("id"), RecordCount("x"))
      )
    assert(atumContextWithTwoRecordCount.currentMeasures.size == 2)

    val atumContextWithTwoDistinctRecordCount =
      atumContextWithRecordCount.addMeasures(
        Set(RecordCount("id"), RecordCount("one"))
      )

    assert(atumContextWithTwoDistinctRecordCount.currentMeasures.size == 3)
  }

  "withMeasureRemoved" should "remove a measure if exists" in {

    val atumContext = AtumAgent.getOrCreateAtumContext(AtumPartitions("foo2"->"bar"))
    assert(atumContext.currentMeasures.isEmpty)

    val atumContext1 = atumContext.addMeasures(
      Set(RecordCount("id"), RecordCount("id"), RecordCount("other"))
    )
    assert(atumContext1.currentMeasures.size == 2)

    val atumContextRemoved = atumContext1.removeMeasure(RecordCount("id"))
    assert(atumContextRemoved.currentMeasures.size == 1)
    assert(atumContextRemoved.currentMeasures.head == RecordCount("other"))
  }

  "createCheckpoint" should "take measurements and create a Checkpoint" in {
    val mockAgent = mock(classOf[AtumAgent])

    val atumContext = new AtumContext(AtumPartitions("foo2" -> "bar"), mockAgent)
      .addMeasure(RecordCount("letter"))

    val spark = SparkSession.builder
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    import spark.implicits._

    val rdd = spark.sparkContext.parallelize(Seq("A", "B", "C"))
    val df = rdd.toDF("letter")

    val checkpoint = atumContext.createCheckpoint("testCheckpoint", "Hans", df)

    assert(checkpoint.name == "testCheckpoint")
    assert(checkpoint.author == "Hans")
    assert(checkpoint.atumPartitions == AtumPartitions("foo2", "bar"))
    assert(checkpoint.measurements.head.resultValue == "3")
  }

  "createCheckpointOnProvidedData" should "create a Checkpoint on provided data" in {
    val atumAgent = new AtumAgent
    val atumPartitions = AtumPartitions("key" -> "value")
    val atumContext = atumAgent.getOrCreateAtumContext(atumPartitions)

    val measurements = Seq(
      MeasurementProvided(RecordCount("col"), 1L),
      MeasurementProvided(SumOfValuesOfColumn("col"), BigDecimal(1))
    )

    val checkpoint = atumContext.createCheckpointOnProvidedData(
      checkpointName = "name",
      author = "author",
      measurements = measurements
    )

    assert(checkpoint.name == "name")
    assert(checkpoint.author == "author")
    assert(!checkpoint.measuredByAtumAgent)
    assert(checkpoint.atumPartitions == atumPartitions)
    assert(checkpoint.processStartTime == checkpoint.processEndTime.get)
    assert(checkpoint.measurements == measurements)
  }

  "createAndSaveCheckpoint" should "take measurements and create a Checkpoint, multiple measure changes" in {
    val mockAgent = mock(classOf[AtumAgent])

    implicit val atumContext: AtumContext = new AtumContext(AtumPartitions("foo2" -> "bar"), mockAgent)
      .addMeasure(RecordCount("notImportantColumn"))

    val spark = SparkSession.builder
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    val rdd = spark.sparkContext.parallelize(
      Seq(
        Row("A", 8.0),
        Row("B", 2.9),
        Row("C", 9.1),
        Row("D", 2.5)
      )
    )
    val schema = new StructType()
      .add(StructField("notImportantColumn", StringType))
      .add(StructField("columnForSum", DoubleType))

    import AtumContext._

    val df = spark.createDataFrame(rdd, schema)
      .createAndSaveCheckpoint("checkPointNameCount", "authorOfCount")

    val argumentFirst = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent, times(1)).saveCheckpoint(argumentFirst.capture())

    assert(argumentFirst.getValue.name == "checkPointNameCount")
    assert(argumentFirst.getValue.author == "authorOfCount")
    assert(argumentFirst.getValue.partitioning == Seq(PartitionDTO("foo2", "bar")))
    assert(argumentFirst.getValue.measurements.head.result.mainValue.value == "4")
    assert(argumentFirst.getValue.measurements.head.result.mainValue.valueType == ResultValueType.Long)

    atumContext.addMeasure(SumOfValuesOfColumn("columnForSum"))
    df.createAndSaveCheckpoint("checkPointNameSum", "authorOfSum")

    val argumentSecond = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent, times(2)).saveCheckpoint(argumentSecond.capture())

    assert(argumentSecond.getValue.name == "checkPointNameSum")
    assert(argumentSecond.getValue.author == "authorOfSum")
    assert(argumentSecond.getValue.partitioning == Seq(PartitionDTO("foo2", "bar")))
    assert(argumentSecond.getValue.measurements.tail.head.result.mainValue.value == "22.5")
    assert(argumentSecond.getValue.measurements.tail.head.result.mainValue.valueType == ResultValueType.BigDecimal)
  }

  "createCheckpoint" should "take measurements and fail because numeric measure is defined on non-numeric column" in {
    val mockAgent = mock(classOf[AtumAgent])

    implicit val atumContext: AtumContext = new AtumContext(AtumPartitions("foo2" -> "bar"), mockAgent)
      .addMeasure(SumOfValuesOfColumn("nonNumericalColumn"))

    val spark = SparkSession.builder
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    val rdd = spark.sparkContext.parallelize(
      Seq(
        Row("A", 8.0),
        Row("B", 2.9),
        Row("C", 9.1),
        Row("D", 2.5)
      )
    )
    val schema = new StructType()
      .add(StructField("nonNumericalColumn", StringType))
      .add(StructField("numericalColumn", DoubleType))

    import AtumContext._

    val df = spark.createDataFrame(rdd, schema)

    val caughtException = the[IllegalArgumentException] thrownBy {
      df.createAndSaveCheckpoint("checkPointNameCountInvalid", "authorOfCount")
    }
    caughtException.getMessage should include(
      "Column nonNumericalColumn measurement aggregatedTotal requested, " +
        "but the field is not numeric! Found: string datatype."
    )
  }

  "createCheckpoint" should "take measurements and fail because column doesn't exist" in {
    val mockAgent = mock(classOf[AtumAgent])

    implicit val atumContext: AtumContext = new AtumContext(AtumPartitions("foo2" -> "bar"), mockAgent)
      .addMeasure(RecordCount("nonExistingColumn"))

    val spark = SparkSession.builder
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    val rdd = spark.sparkContext.parallelize(
      Seq(
        Row("A", 8.0),
        Row("B", 2.9),
        Row("C", 9.1),
        Row("D", 2.5)
      )
    )
    val schema = new StructType()
      .add(StructField("nonNumericalColumn", StringType))
      .add(StructField("numericalColumn", DoubleType))

    import AtumContext._

    val df = spark.createDataFrame(rdd, schema)

    val caughtException = the[IllegalArgumentException] thrownBy {
      df.createAndSaveCheckpoint("checkPointNameCountColNonExisting", "authorOfCount")
    }
    caughtException.getMessage should include(
      "Column(s) 'nonExistingColumn' must be present in dataframe, but it's not. " +
        s"Columns in the dataframe: nonNumericalColumn, numericalColumn."
    )
  }

}

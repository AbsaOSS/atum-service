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
import org.mockito.Mockito.{mock, times, verify, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.AtumContext.AtumPartitions
import za.co.absa.atum.agent.model.AtumMeasure.{RecordCount, SumOfValuesOfColumn}
import za.co.absa.atum.agent.model.{Measure, MeasureResult, MeasurementBuilder, UnknownMeasure}
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType

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

    val authorTest = "authorTest"
    when(mockAgent.currentUser).thenReturn(authorTest)

    val atumPartitions = AtumPartitions("foo2" -> "bar")

    val atumContext = new AtumContext(atumPartitions, mockAgent)
      .addMeasure(RecordCount("letter"))

    val spark = SparkSession.builder()
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    import spark.implicits._

    val rdd = spark.sparkContext.parallelize(Seq("A", "B", "C"))
    val df = rdd.toDF("letter")

    atumContext.createCheckpoint("testCheckpoint", df)

    val argument = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent).saveCheckpoint(argument.capture())

    assert(argument.getValue.name == "testCheckpoint")
    assert(argument.getValue.author == authorTest)
    assert(argument.getValue.partitioning == AtumPartitions.toSeqPartitionDTO(atumPartitions))
    assert(argument.getValue.measurements.head.result.mainValue.value == "3")
    assert(argument.getValue.measurements.head.result.mainValue.valueType == ResultValueType.Long)
  }

  "createCheckpointOnProvidedData" should "create a Checkpoint on provided data" in {
    val mockAgent = mock(classOf[AtumAgent])

    val authorTest = "authorTest"
    when(mockAgent.currentUser).thenReturn(authorTest)

    val atumPartitions = AtumPartitions("key" -> "value")
    val atumContext: AtumContext = new AtumContext(atumPartitions, mockAgent)

    val measurements: Map[Measure, MeasureResult] = Map(
      RecordCount("col")          -> MeasureResult(1L),
      SumOfValuesOfColumn("col")  -> MeasureResult(BigDecimal(1)),
      UnknownMeasure("customMeasureName", Seq("col"), ResultValueType.BigDecimal) -> MeasureResult(BigDecimal(1))
    )

    atumContext.createCheckpointOnProvidedData(
      checkpointName = "name", measurements = measurements)

    val argument = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent).saveCheckpoint(argument.capture())

    assert(argument.getValue.name == "name")
    assert(argument.getValue.author == authorTest)
    assert(!argument.getValue.measuredByAtumAgent)
    assert(argument.getValue.partitioning == AtumPartitions.toSeqPartitionDTO(atumPartitions))
    assert(argument.getValue.processStartTime == argument.getValue.processEndTime.get)
    assert(argument.getValue.measurements == MeasurementBuilder.buildAndValidateMeasurementsDTO(measurements))
  }

  "createCheckpoint" should "take measurements and create a Checkpoint, multiple measure changes" in {
    val mockAgent = mock(classOf[AtumAgent])

    val authorTest = "authorTest"
    when(mockAgent.currentUser).thenReturn(authorTest)

    val atumPartitions = AtumPartitions("foo2" -> "bar")
    implicit val atumContext: AtumContext = new AtumContext(atumPartitions, mockAgent)
      .addMeasure(RecordCount("notImportantColumn"))

    val spark = SparkSession.builder()
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
      .createCheckpoint("checkPointNameCount")

    val argumentFirst = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent, times(1)).saveCheckpoint(argumentFirst.capture())

    assert(argumentFirst.getValue.name == "checkPointNameCount")
    assert(argumentFirst.getValue.author == authorTest)
    assert(argumentFirst.getValue.partitioning == AtumPartitions.toSeqPartitionDTO(atumPartitions))
    assert(argumentFirst.getValue.measurements.head.result.mainValue.value == "4")
    assert(argumentFirst.getValue.measurements.head.result.mainValue.valueType == ResultValueType.Long)

    atumContext.addMeasure(SumOfValuesOfColumn("columnForSum"))
    when(mockAgent.currentUser).thenReturn(authorTest + "Another")  // maybe a process changed the author / current user
    df.createCheckpoint("checkPointNameSum")

    val argumentSecond = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent, times(2)).saveCheckpoint(argumentSecond.capture())

    assert(argumentSecond.getValue.name == "checkPointNameSum")
    assert(argumentSecond.getValue.author == authorTest + "Another")
    assert(argumentSecond.getValue.partitioning == AtumPartitions.toSeqPartitionDTO(atumPartitions))
    assert(argumentSecond.getValue.measurements.tail.head.result.mainValue.value == "22.5")
    assert(argumentSecond.getValue.measurements.tail.head.result.mainValue.valueType == ResultValueType.BigDecimal)
  }

  "addAdditionalData" should "add key/value pair to map for additional data" in {
    val atumAgent = AtumAgent
    val atumPartitions = AtumPartitions("key" -> "val")
    val atumContext = atumAgent.getOrCreateAtumContext(atumPartitions)

    val additionalDataKey = "additionalKey"
    val additionalDataValue = "additionalVal"
    val expectedAdditionalData =  Map(additionalDataKey -> Some(additionalDataValue))

    atumContext.addAdditionalData(additionalDataKey, additionalDataValue)

    assert(atumContext.currentAdditionalData == expectedAdditionalData)
  }
}

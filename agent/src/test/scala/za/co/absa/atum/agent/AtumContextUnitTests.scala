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
import za.co.absa.atum.agent.model.AtumMeasure.{DistinctRecordCount, RecordCount, SumOfValuesOfColumn}
import za.co.absa.atum.agent.model.{Measure, MeasureResult, MeasurementBuilder, UnknownMeasure}
import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.model.types.basic._

class AtumContextUnitTests extends AnyFlatSpec with Matchers {

  "withMeasureAddedOrOverwritten" should "add a new measure if not exists, overwrite it otherwise" in {

    val atumContext = AtumAgent.getOrCreateAtumContext(AtumPartitions("foo1"->"bar"))

    assert(atumContext.currentMeasures.isEmpty)

    val atumContextWithRecordCount =
      atumContext.addMeasure(RecordCount())
    assert(atumContextWithRecordCount.currentMeasures.size == 1)

    val atumContextWithTwoRecordCount =
      atumContextWithRecordCount.addMeasures(
        Set(RecordCount(), DistinctRecordCount(Seq("col")))
      )
    assert(atumContextWithTwoRecordCount.currentMeasures.size == 2)
  }

  "withMeasureRemoved" should "remove a measure if exists" in {

    val atumContext = AtumAgent.getOrCreateAtumContext(AtumPartitions("foo2"->"bar"))
    assert(atumContext.currentMeasures.isEmpty)

    val atumContext1 = atumContext.addMeasures(
      Set(RecordCount())
    )
    assert(atumContext1.currentMeasures.size == 1)

    val atumContextRemoved = atumContext1.removeMeasure(RecordCount())
    assert(atumContextRemoved.currentMeasures.isEmpty)
  }

  "createCheckpoint" should "take measurements and create a Checkpoint; including it's properties" in {
    val mockAgent = mock(classOf[AtumAgent])

    val checkpointProperties = Some(Map("prop1" -> "val1", "prop2" -> "val2"))

    val authorTest = "authorTest"
    when(mockAgent.currentUser).thenReturn(authorTest)

    val atumPartitions = AtumPartitions("foo2" -> "bar")

    val atumContext = new AtumContext(atumPartitions, mockAgent)
      .addMeasure(RecordCount())

    val spark = SparkSession.builder()
      .master("local")
      .config("spark.driver.host", "localhost")
      .config("spark.ui.enabled", "false")
      .getOrCreate()

    import spark.implicits._

    val rdd = spark.sparkContext.parallelize(Seq("A", "B", "C"))
    val df = rdd.toDF("letter")

    atumContext.createCheckpoint("testCheckpoint", df, checkpointProperties)

    val argument = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent).saveCheckpoint(argument.capture())
    val value: CheckpointDTO = argument.getValue
    assert(value.name == "testCheckpoint")
    assert(value.author == authorTest)
    assert(value.partitioning == atumPartitions.toPartitioningDTO)
    assert(value.measurements.head.result.mainValue.value == "3")
    assert(value.measurements.head.result.mainValue.valueType == ResultValueType.LongValue)
    assert(value.properties == checkpointProperties)
  }

  "createCheckpointOnProvidedData" should "create a Checkpoint on provided data; including it's properties" in {
    val mockAgent = mock(classOf[AtumAgent])

    val checkpointProperties = Some(Map("prop1" -> "val1", "prop2" -> "val2"))

    val authorTest = "authorTest"
    when(mockAgent.currentUser).thenReturn(authorTest)

    val atumPartitions = AtumPartitions("key" -> "value")
    val atumContext: AtumContext = new AtumContext(atumPartitions, mockAgent)

    val measurements: Map[Measure, MeasureResult] = Map(
      RecordCount()         -> MeasureResult(1L),
      SumOfValuesOfColumn("col")  -> MeasureResult(BigDecimal(1)),
      UnknownMeasure("customMeasureName", Seq("col"), ResultValueType.BigDecimalValue) -> MeasureResult(BigDecimal(1))
    )

    atumContext.createCheckpointOnProvidedData(
      checkpointName = "name", measurements = measurements, properties = checkpointProperties
    )

    val argument = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent).saveCheckpoint(argument.capture())
    val value: CheckpointDTO = argument.getValue

    assert(value.name == "name")
    assert(value.author == authorTest)
    assert(!value.measuredByAtumAgent)
    assert(value.partitioning == atumPartitions.toPartitioningDTO)
    assert(value.processStartTime == value.processEndTime.get)
    assert(value.measurements == MeasurementBuilder.buildAndValidateMeasurementsDTO(measurements))
    assert(value.properties == checkpointProperties)
  }

  "createCheckpoint" should "take measurements and create a Checkpoint, multiple measure changes" in {
    val mockAgent = mock(classOf[AtumAgent])

    val authorTest = "authorTest"
    when(mockAgent.currentUser).thenReturn(authorTest)

    val atumPartitions = AtumPartitions("foo2" -> "bar")
    implicit val atumContext: AtumContext = new AtumContext(atumPartitions, mockAgent)
      .addMeasure(RecordCount())

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
    val valueFirst: CheckpointDTO = argumentFirst.getValue

    assert(valueFirst.name == "checkPointNameCount")
    assert(valueFirst.author == authorTest)
    assert(valueFirst.partitioning == atumPartitions.toPartitioningDTO)
    assert(valueFirst.measurements.head.result.mainValue.value == "4")
    assert(valueFirst.measurements.head.result.mainValue.valueType == ResultValueType.LongValue)

    atumContext.addMeasure(SumOfValuesOfColumn("columnForSum"))
    when(mockAgent.currentUser).thenReturn(authorTest + "Another")  // maybe a process changed the author / current user
    df.createCheckpoint("checkPointNameSum")

    val argumentSecond = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent, times(2)).saveCheckpoint(argumentSecond.capture())
    val valueSecond: CheckpointDTO = argumentSecond.getValue

    assert(valueSecond.name == "checkPointNameSum")
    assert(valueSecond.author == authorTest + "Another")
    assert(valueSecond.partitioning == atumPartitions.toPartitioningDTO)
    assert(valueSecond.measurements.tail.head.result.mainValue.value == "22.5")
    assert(valueSecond.measurements.tail.head.result.mainValue.valueType == ResultValueType.BigDecimalValue)
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

  "DatasetWrapper.createCheckpoint" should "create a checkpoint with properties" in {
    val spark = SparkSession.builder().master("local").appName("test").getOrCreate()
    import spark.implicits._

    val df = Seq((1, "a"), (2, "b")).toDF("id", "value")

    // Mock AtumAgent to capture the checkpoint
    val mockAgent = mock(classOf[za.co.absa.atum.agent.AtumAgent])
    val atumPartitions = AtumPartitions("key" -> "val")

    implicit val atumContext: AtumContext = new AtumContext(
      atumPartitions = atumPartitions,
      agent = mockAgent
    )

    val properties = Some(Map("prop1" -> "value1", "prop2" -> "value2"))

    import AtumContext._
    df.createCheckpoint("testCheckpoint", properties)

    val argument = ArgumentCaptor.forClass(classOf[CheckpointDTO])
    verify(mockAgent).saveCheckpoint(argument.capture())
    val value: CheckpointDTO = argument.getValue

    value.name shouldBe "testCheckpoint"
    value.properties shouldBe properties
  }

}

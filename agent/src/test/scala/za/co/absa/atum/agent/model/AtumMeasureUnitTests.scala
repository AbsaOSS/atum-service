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

package za.co.absa.atum.agent.model

import org.apache.spark.sql.Row
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.AtumAgent
import za.co.absa.atum.agent.AtumContext.DatasetWrapper
import za.co.absa.atum.agent.model.AtumMeasure._
import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.types.basic.AtumPartitions
import za.co.absa.spark.commons.test.SparkTestBase

class AtumMeasureUnitTests extends AnyFlatSpec with Matchers with SparkTestBase { self =>

  "Measure" should "be based on the dataframe" in {

    // Measures
    val measureIds: AtumMeasure     = RecordCount()
    val salaryAbsSum: AtumMeasure   = AbsSumOfValuesOfColumn(measuredCol = "salary")
    val salarySum                   = SumOfValuesOfColumn(measuredCol = "salary")
    val salaryTruncSum              = SumOfTruncatedValuesOfColumn(measuredCol = "salary")
    val salaryAbsTruncSum           = AbsSumOfTruncatedValuesOfColumn(measuredCol = "salary")
    val sumOfHashes: AtumMeasure    = SumOfHashesOfColumn(measuredCol = "id")

    // AtumContext contains `Measurement`
    val atumContextInstanceWithRecordCount = AtumAgent
      .getOrCreateAtumContext(AtumPartitions("foo" -> "bar"))
      .addMeasure(measureIds)
    val atumContextWithSalaryAbsMeasure = atumContextInstanceWithRecordCount
      .subPartitionContext(AtumPartitions("sub" -> "partition"))
      .addMeasure(salaryAbsSum)
    val atumContextWithNameHashSum = atumContextInstanceWithRecordCount
      .subPartitionContext(AtumPartitions("another" -> "partition"))
      .addMeasure(sumOfHashes)

    // Pipeline
    val dfPersons = spark.read
      .format("csv")
      .option("header", "true")
      .load("agent/src/test/resources/random-dataset/persons.csv")
      .createCheckpoint("name1")(atumContextInstanceWithRecordCount)
      .createCheckpoint("name2")(atumContextWithNameHashSum)

    val dsEnrichment = spark.read
      .format("csv")
      .option("header", "true")
      .load("agent/src/test/resources/random-dataset/persons-enriched.csv")
      .createCheckpoint("name3")(
        atumContextWithSalaryAbsMeasure.removeMeasure(salaryAbsSum)
      )

    val dfFull = dfPersons
      .join(dsEnrichment, Seq("id"))
      .createCheckpoint("other different name")(atumContextWithSalaryAbsMeasure)

    val dfExtraPersonWithNegativeSalary = spark
      .createDataFrame(
        Seq(
          ("id", "firstName", "lastName", "email", "email2", "profession", "-1000")
        )
      )
      .toDF("id", "firstName", "lastName", "email", "email2", "profession", "salary")

    val dfExtraPerson = dfExtraPersonWithNegativeSalary.union(dfPersons)

    dfExtraPerson.createCheckpoint("a checkpoint name")(
      atumContextWithSalaryAbsMeasure
        .removeMeasure(measureIds)
        .removeMeasure(salaryAbsSum)
    )

    val dfExtraPersonWithDecimalSalary = spark
      .createDataFrame(
        Seq(
          ("id", "firstName", "lastName", "email", "email2", "profession", "3000.98"),
          ("id", "firstName", "lastName", "email", "email2", "profession", "-1000.76")
        )
      )
      .toDF("id", "firstName", "lastName", "email", "email2", "profession", "salary")

    val dfExtraDecimalPerson = dfExtraPersonWithDecimalSalary.union(dfPersons)

    dfExtraDecimalPerson.createCheckpoint("a checkpoint name")(
      atumContextWithSalaryAbsMeasure
        .removeMeasure(measureIds)
        .removeMeasure(salaryAbsSum)
    )


    val dfPersonCntResult = measureIds.function(dfPersons)
    val dfFullCntResult = measureIds.function(dfFull)
    val dfFullSalaryAbsSumResult = salaryAbsSum.function(dfFull)
    val dfFullHashResult = sumOfHashes.function(dfFull)
    val dfExtraPersonSalarySumResult = salarySum.function(dfExtraPerson)
    val dfFullSalarySumResult = salarySum.function(dfFull)
    val dfExtraPersonSalarySumTruncResult = salaryTruncSum.function(dfExtraDecimalPerson)
    val dfFullSalarySumTruncResult = salaryTruncSum.function(dfFull)
    val dfExtraPersonSalaryAbsSumTruncResult = salaryAbsTruncSum.function(dfExtraDecimalPerson)
    val dfFullSalaryAbsSumTruncResult = salaryAbsTruncSum.function(dfFull)

    // Assertions
    assert(dfPersonCntResult.resultValue == "1000")
    assert(dfPersonCntResult.resultValueType == ResultValueType.LongValue)
    assert(dfFullCntResult.resultValue == "1000")
    assert(dfFullCntResult.resultValueType == ResultValueType.LongValue)
    assert(dfFullSalaryAbsSumResult.resultValue == "2987144")
    assert(dfFullSalaryAbsSumResult.resultValueType == ResultValueType.BigDecimalValue)
    assert(dfFullHashResult.resultValue == "2044144307532")
    assert(dfFullHashResult.resultValueType == ResultValueType.StringValue)
    assert(dfExtraPersonSalarySumResult.resultValue == "2986144")
    assert(dfExtraPersonSalarySumResult.resultValueType == ResultValueType.BigDecimalValue)
    assert(dfFullSalarySumResult.resultValue == "2987144")
    assert(dfFullSalarySumResult.resultValueType == ResultValueType.BigDecimalValue)
    assert(dfExtraPersonSalarySumTruncResult.resultValue == "2989144")
    assert(dfExtraPersonSalarySumTruncResult.resultValueType == ResultValueType.LongValue)
    assert(dfFullSalarySumTruncResult.resultValue == "2987144")
    assert(dfFullSalarySumTruncResult.resultValueType == ResultValueType.LongValue)
    assert(dfExtraPersonSalaryAbsSumTruncResult.resultValue == "2991144")
    assert(dfExtraPersonSalaryAbsSumTruncResult.resultValueType == ResultValueType.LongValue)
    assert(dfFullSalaryAbsSumTruncResult.resultValue == "2987144")
    assert(dfFullSalaryAbsSumTruncResult.resultValueType == ResultValueType.LongValue)
  }

  "AbsSumOfValuesOfColumn" should "return expected value" in {
    val salaryAbsSum: AtumMeasure = AbsSumOfValuesOfColumn("salary")

    val data = List(Row("-100.10"), Row("200.20"))
    val rdd = spark.sparkContext.parallelize(data)

    val schema = StructType(Array(StructField("salary", StringType)))
    val df = spark.createDataFrame(rdd, schema)

    val result = salaryAbsSum.function(df)

    assert(result.resultValue == "300.3")
    assert(result.resultValueType == ResultValueType.BigDecimalValue)
  }

  "AbsSumOfValuesOfColumn" should "return expected value for null result" in {
    val salaryAbsSum = AbsSumOfValuesOfColumn("salary")

    val data = List(Row(null), Row(null))
    val rdd = spark.sparkContext.parallelize(data)

    val schema = StructType(Array(StructField("salary", StringType)))
    val df = spark.createDataFrame(rdd, schema)

    val result = salaryAbsSum.function(df)

    assert(result.resultValue == "0")
    assert(result.resultValueType == ResultValueType.BigDecimalValue)
  }

  "RecordCount" should "return expected value" in {
    val distinctCount = RecordCount()

    val data = List(Row("a1", "b1"), Row("a1", "b2"), Row("a2", "b2"), Row("a2", "b2"))
    val rdd = spark.sparkContext.parallelize(data)

    val schema = StructType(Array(StructField("colA", StringType), StructField("colB", StringType)))
    val df = spark.createDataFrame(rdd, schema)

    val result = distinctCount.function(df)

    assert(result.resultValue == "4")
    assert(result.resultValueType == ResultValueType.LongValue)
  }

  "DistinctRecordCount" should "return expected value for multiple columns" in {
    val distinctCount = DistinctRecordCount(Seq("colA", "colB"))

    val data = List(Row("a1", "b1"), Row("a1", "b2"), Row("a2", "b2"), Row("a2", "b2"))
    val rdd = spark.sparkContext.parallelize(data)

    val schema = StructType(Array(StructField("colA", StringType), StructField("colB", StringType)))
    val df = spark.createDataFrame(rdd, schema)

    val result = distinctCount.function(df)

    assert(result.resultValue == "3")
    assert(result.resultValueType == ResultValueType.LongValue)
  }

  "DistinctRecordCount" should "fail requirements when no control columns given" in {
    assertThrows[java.lang.IllegalArgumentException](DistinctRecordCount(measuredCols = Seq.empty))
  }

  "SumOfValuesOfColumn" should "return expected value" in {
    val distinctCount = SumOfValuesOfColumn("colA")

    val data = List(Row(1, "b1"), Row(1, "b2"), Row(1, "b2"), Row(1, "b2"))
    val rdd = spark.sparkContext.parallelize(data)

    val schema = StructType(Array(StructField("colA", IntegerType), StructField("colB", StringType)))
    val df = spark.createDataFrame(rdd, schema)

    val result = distinctCount.function(df)

    assert(result.resultValue == "4")
    assert(result.resultValueType == ResultValueType.BigDecimalValue)
  }

  "SumTruncOfValuesOfColumn" should "return expected value" in {
    val distinctCount = SumOfTruncatedValuesOfColumn("colA")

    val data = List(Row("1.98", "b1"), Row("-1.76", "b2"), Row("1.54", "b2"), Row("1.32", "b2"))
    val rdd = spark.sparkContext.parallelize(data)

    val schema = StructType(Array(StructField("colA", StringType), StructField("colB", StringType)))
    val df = spark.createDataFrame(rdd, schema)

    val result = distinctCount.function(df)

    assert(result.resultValue == "2")
    assert(result.resultValueType == ResultValueType.LongValue)
  }

  "AbsSumTruncOfValuesOfColumn" should "return expected value" in {
    val distinctCount = AbsSumOfTruncatedValuesOfColumn("colA")

    val data = List(Row("1.98", "b1"), Row("-1.76", "b2"), Row("1.54", "b2"), Row("-1.32", "b2"))
    val rdd = spark.sparkContext.parallelize(data)

    val schema = StructType(Array(StructField("colA", StringType), StructField("colB", StringType)))
    val df = spark.createDataFrame(rdd, schema)

    val result = distinctCount.function(df)

    assert(result.resultValue == "4")
    assert(result.resultValueType == ResultValueType.LongValue)
  }
}

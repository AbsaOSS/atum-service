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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.AtumAgent
import za.co.absa.atum.agent.AtumContext.{AtumPartitions, DatasetWrapper}
import za.co.absa.atum.agent.model.Measure._
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType
import za.co.absa.spark.commons.test.SparkTestBase

class MeasureTest extends AnyFlatSpec with Matchers with SparkTestBase { self =>

  "Measure" should "be based on the dataframe" in {

    // Measures
    val measureIds: Measure = RecordCount(controlCol = "id")
    val salaryAbsSum: Measure = AbsSumOfValuesOfColumn(
      controlCol = "salary"
    )
    val salarySum = SumOfValuesOfColumn(controlCol = "salary")
    val sumOfHashes: Measure = SumOfHashesOfColumn(controlCol = "id")

    // AtumContext contains `Measurement`
    val atumContextInstanceWithRecordCount = AtumAgent
      .getOrCreateAtumContext(AtumPartitions("foo"->"bar"))
      .addMeasure(measureIds)
    val atumContextWithSalaryAbsMeasure = atumContextInstanceWithRecordCount
      .subPartitionContext(AtumPartitions("sub"->"partition"))
      .addMeasure(salaryAbsSum)
    val atumContextWithNameHashSum = atumContextInstanceWithRecordCount
      .subPartitionContext(AtumPartitions("another"->"partition"))
      .addMeasure(sumOfHashes)

    // Pipeline
    val dfPersons = spark.read
      .format("csv")
      .option("header", "true")
      .load("agent/src/test/resources/random-dataset/persons.csv")
      .createCheckpoint("name1", "author")(atumContextInstanceWithRecordCount)
      .createCheckpoint("name2", "author")(atumContextWithNameHashSum)

    val dsEnrichment = spark.read
      .format("csv")
      .option("header", "true")
      .load("agent/src/test/resources/random-dataset/persons-enriched.csv")
      .createCheckpoint("name3", "author")(
        atumContextWithSalaryAbsMeasure.removeMeasure(salaryAbsSum)
      )

    val dfFull = dfPersons
      .join(dsEnrichment, Seq("id"))
      .createCheckpoint("other different name", "author")(atumContextWithSalaryAbsMeasure)

    val dfExtraPersonWithNegativeSalary = spark
      .createDataFrame(
        Seq(
          ("id", "firstName", "lastName", "email", "email2", "profession", "-1000")
        )
      )
      .toDF("id", "firstName", "lastName", "email", "email2", "profession", "salary")

    val dfExtraPerson = dfExtraPersonWithNegativeSalary.union(dfPersons)

    dfExtraPerson.createCheckpoint("a checkpoint name", "author")(
      atumContextWithSalaryAbsMeasure
        .removeMeasure(measureIds)
        .removeMeasure(salaryAbsSum)
    )

    val dfPersonCntResult             = measureIds.function(dfPersons)
    val dfFullCntResult               = measureIds.function(dfFull)
    val dfFullSalaryAbsSumResult      = salaryAbsSum.function(dfFull)
    val dfFullHashResult              = sumOfHashes.function(dfFull)
    val dfExtraPersonSalarySumResult  = salarySum.function(dfExtraPerson)
    val dfFullSalarySumResult         = salarySum.function(dfFull)

    // Assertions
    assert(dfPersonCntResult.resultValue == "1000")
    assert(dfPersonCntResult.resultType == ResultValueType.Long)
    assert(dfFullCntResult.resultValue == "1000")
    assert(dfFullCntResult.resultType == ResultValueType.Long)
    assert(dfFullSalaryAbsSumResult.resultValue == "2987144")
    assert(dfFullSalaryAbsSumResult.resultType == ResultValueType.Double)
    assert(dfFullHashResult.resultValue == "2044144307532")
    assert(dfFullHashResult.resultType == ResultValueType.String)
    assert(dfExtraPersonSalarySumResult.resultValue == "2986144")
    assert(dfExtraPersonSalarySumResult.resultType == ResultValueType.BigDecimal)
    assert(dfFullSalarySumResult.resultValue == "2987144")
    assert(dfFullSalarySumResult.resultType == ResultValueType.BigDecimal)
  }

}

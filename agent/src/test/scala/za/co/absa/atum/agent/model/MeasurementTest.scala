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
import za.co.absa.atum.agent.exception.AtumAgentException.MeasurementException
import za.co.absa.atum.agent.model.AtumMeasure._
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType
import za.co.absa.spark.commons.test.SparkTestBase

class MeasurementTest extends AnyFlatSpec with Matchers with SparkTestBase { self =>

  "Measurement" should "be able to be converted to MeasurementProvided object when the result is BigDecimal" in {
    val measure = AbsSumOfValuesOfColumn("col")
    val actualMeasurement = Measurement(measure, MeasureResult(BigDecimal(1.0)))

    assert(actualMeasurement.result.resultValue == 1.0)
    assert(actualMeasurement.result.resultType == ResultValueType.BigDecimal)
  }

  "Measurement" should "be able derive correct result type when the result is Double" in {
    val measure = AbsSumOfValuesOfColumn("col")
    val actualMeasurement = Measurement(measure, MeasureResult(1.0))

    assert(actualMeasurement.result.resultValue == 1.0)
    assert(actualMeasurement.result.resultType == ResultValueType.Double)
  }

  "MeasurementProvided" should "throw exception for unsupported result value - Double instead of BigDecimal" in {
    val measure = AbsSumOfValuesOfColumn("col")
    assertThrows[MeasurementException](Measurement(measure, MeasureResult(1.0)))
  }

  "Measurement" should "throw exception for unsupported result value type in general (scalar)" in {
    val measure = SumOfValuesOfColumn("col")
    assertThrows[MeasurementException](Measurement(measure, MeasureResult(1)))
  }

  "Measurement" should "throw exception for unsupported result value type in general (composite)" in {
    val measure = SumOfHashesOfColumn("col")
    assertThrows[MeasurementException](Measurement(measure, MeasureResult(Map(1 -> "no-go"))))
  }

  "Measurement" should "throw exception for unsupported result value type for a given Measure" in {
    val measure = DistinctRecordCount(Seq("col"))
    assertThrows[MeasurementException](Measurement(measure, MeasureResult("1")))
  }

  "Measurement" should "throw exception for unsupported (slightly different FPN) result value type for a given Measure" in {
    val measure = SumOfValuesOfColumn("col")
    assertThrows[MeasurementException](Measurement(measure, MeasureResult(1.0)))
  }
}

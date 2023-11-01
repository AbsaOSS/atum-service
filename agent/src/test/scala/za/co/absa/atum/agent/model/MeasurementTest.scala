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
import za.co.absa.atum.agent.exception.MeasurementProvidedException
import za.co.absa.atum.agent.model.AtumMeasure._
import za.co.absa.atum.agent.model.Measurement.MeasurementProvided
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType
import za.co.absa.spark.commons.test.SparkTestBase

class MeasurementTest extends AnyFlatSpec with Matchers with SparkTestBase { self =>

  "MeasurementProvided" should "be able to be converted to MeasurementProvided object when the result is Double" in {
    val measure = AbsSumOfValuesOfColumn("col")
    val actualMeasurement = MeasurementProvided(measure, 1.0)

    assert(actualMeasurement.resultValue == 1.0)
    assert(actualMeasurement.resultType == ResultValueType.Double)
  }

  "MeasurementProvided" should "throw exception for unsupported result value - BigDecimal instead of Double" in {
    val measure = AbsSumOfValuesOfColumn("col")
    assertThrows[MeasurementProvidedException](MeasurementProvided(measure, BigDecimal(1.0)))
  }

  "MeasurementProvided" should "throw exception for unsupported result value type in general (scalar)" in {
    val measure = SumOfValuesOfColumn("col")
    assertThrows[MeasurementProvidedException](MeasurementProvided(measure, 1))
  }

  "MeasurementProvided" should "throw exception for unsupported result value type in general (composite)" in {
    val measure = SumOfHashesOfColumn("col")
    assertThrows[MeasurementProvidedException](MeasurementProvided(measure, Map(1 -> "no-go")))
  }

  "MeasurementProvided" should "throw exception for unsupported result value type for a given Measure" in {
    val measure = DistinctRecordCount("col")
    assertThrows[MeasurementProvidedException](MeasurementProvided(measure, "1"))
  }

  "MeasurementProvided" should "throw exception for unsupported (slightly different FPN) result value type for a given Measure" in {
    val measure = SumOfValuesOfColumn("col")
    assertThrows[MeasurementProvidedException](MeasurementProvided(measure, 1.0))
  }
}

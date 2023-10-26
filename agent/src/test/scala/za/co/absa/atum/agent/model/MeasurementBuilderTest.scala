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
import za.co.absa.atum.agent.model.Measure.SumOfValuesOfColumn
import za.co.absa.atum.model.dto.{MeasureDTO, MeasureResultDTO}
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}

class MeasurementBuilderTest extends AnyFlatSpec {

  "buildMeasurementDTO" should "build MeasurementDTO for Long type of result value when Measurement provided" in {
    val measure = SumOfValuesOfColumn("col")
    val measurement = MeasurementProvided(measure, BigDecimal(1))
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedMeasureDTO = MeasureDTO("aggregatedTotal", Seq("col"))

    val expectedMeasureResultDTO = MeasureResultDTO(
      TypedValue("1", ResultValueType.BigDecimal)
    )

    assert(measurementDTO.measure == expectedMeasureDTO)
    assert(measurementDTO.result == expectedMeasureResultDTO)
  }

  "buildMeasurementDTO" should "build MeasurementDTO for BigDecimal type of result value when Measurement provided" in {
    val measure = SumOfValuesOfColumn("col")
    val measurement = MeasurementProvided(measure, BigDecimal(3.14))
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedTypedValue = TypedValue("3.14", ResultValueType.BigDecimal)

    assert(measurementDTO.result.mainValue == expectedTypedValue)
  }

  "buildMeasurementDTO" should "not build MeasurementDTO for incompatible String type of result value when Measurement provided" in {
    val measure = SumOfValuesOfColumn("col")
    val measurement = MeasurementByAtum(measure, "stringValue", ResultValueType.String)

    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedTypedValue = TypedValue("stringValue", ResultValueType.String)

    assert(measurementDTO.result.mainValue == expectedTypedValue)
  }

  "buildMeasurementDTO" should "build MeasurementDTO for BigDecimal type of result value when measured by Agent" in {
    val measure = SumOfValuesOfColumn("col")
    val measurement = MeasurementByAtum(measure, "1", ResultValueType.BigDecimal)
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedMeasureDTO = MeasureDTO("aggregatedTotal", Seq("col"))

    val expectedMeasureResultDTO = MeasureResultDTO(
      TypedValue("1", ResultValueType.BigDecimal)
    )

    assert(measurementDTO.measure == expectedMeasureDTO)
    assert(measurementDTO.result == expectedMeasureResultDTO)
  }

}

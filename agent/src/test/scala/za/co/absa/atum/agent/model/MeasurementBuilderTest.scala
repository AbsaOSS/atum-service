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
import za.co.absa.atum.agent.exception.MeasurementException
import za.co.absa.atum.agent.model.Measure.{AbsSumOfValuesOfColumn, DistinctRecordCount, SumOfValuesOfColumn}
import za.co.absa.atum.model.dto.{MeasureDTO, MeasureResultDTO, MeasurementDTO}
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}

class MeasurementBuilderTest extends AnyFlatSpec {

  "buildMeasurementDTO" should
    "build MeasurementDTO for BigDecimal type of result value when Measure and MeasureResult provided" in {

    val measure = SumOfValuesOfColumn("col")
    val measureResult = MeasureResult(BigDecimal(1))

    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measure, measureResult)

    val expectedMeasureDTO = MeasureDTO("aggregatedTotal", Seq("col"))

    val expectedMeasureResultDTO = MeasureResultDTO(
      TypedValue("1", ResultValueType.BigDecimal)
    )

    assert(measurementDTO.measure == expectedMeasureDTO)
    assert(measurementDTO.result == expectedMeasureResultDTO)
  }

  "buildMeasurementDTO" should
    "build MeasurementDTO for BigDecimal type of result value when Measurement provided" in {

    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, MeasureResult(BigDecimal(3.14)))
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedTypedValue = TypedValue("3.14", ResultValueType.BigDecimal)

    assert(measurementDTO.result.mainValue == expectedTypedValue)
  }

  "buildMeasurementDTO" should
    "not build MeasurementDTO for incompatible String type of result value when Measurement provided" in {

    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, MeasureResult("stringValue", ResultValueType.String))

    assertThrows[MeasurementException](MeasurementBuilder.buildMeasurementDTO(measurement))
  }

  "buildMeasurementDTO" should
    "build MeasurementDTO (at least for now) for compatible result type but incompatible actual type of result value " +
      "when Measurement provided" in {

    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, MeasureResult("stringValue", ResultValueType.BigDecimal))

    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedTypedValue = TypedValue("stringValue", ResultValueType.BigDecimal)

    assert(measurementDTO.result.mainValue == expectedTypedValue)
  }

  "buildMeasurementDTO" should
    "build MeasurementDTO for BigDecimal type of result value when measured by Agent" in {

    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, MeasureResult("1", ResultValueType.BigDecimal))
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedMeasureDTO = MeasureDTO("aggregatedTotal", Seq("col"))

    val expectedMeasureResultDTO = MeasureResultDTO(
      TypedValue("1", ResultValueType.BigDecimal)
    )

    assert(measurementDTO.measure == expectedMeasureDTO)
    assert(measurementDTO.result == expectedMeasureResultDTO)
  }

  "buildMeasurementDTO" should
    "throw exception for unsupported (slightly different FPN) result value type for a given Measure" in {

    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, MeasureResult(1.0))

    assertThrows[MeasurementException](MeasurementBuilder.buildMeasurementDTO(measurement))
  }

  "buildMeasurementDTO" should "throw exception for unsupported result value - BigDecimal instead of Double" in {
    val measure = AbsSumOfValuesOfColumn("col")
    val measurement = Measurement(measure, MeasureResult(BigDecimal(1.0)))

    assertThrows[MeasurementException](MeasurementBuilder.buildMeasurementDTO(measurement))
  }

  "buildMeasurementDTO" should "throw exception for unsupported result value type for a given Measure" in {
    val measure = DistinctRecordCount("col")
    val measureResult = MeasureResult("1")

    assertThrows[MeasurementException](MeasurementBuilder.buildMeasurementDTO(measure, measureResult))
  }

  "buildMeasurementDTO" should "build Seq[MeasurementDTO] for multiple measures, all unique" in {
    val measurements = Seq(
      Measurement(DistinctRecordCount("col"), MeasureResult("1", ResultValueType.Long)),
      Measurement(SumOfValuesOfColumn("col1"), MeasureResult(BigDecimal(1.2))),
      Measurement(SumOfValuesOfColumn("col2"), MeasureResult(BigDecimal(1.3)))
    )
    val measurementDTOs = MeasurementBuilder.buildMeasurementDTO(measurements)

    val expectedMeasurementDTO = Seq(
      MeasurementDTO(
        MeasureDTO("distinctCount", Seq("col")), MeasureResultDTO(TypedValue("1", ResultValueType.Long))
      ),
      MeasurementDTO(
        MeasureDTO("aggregatedTotal", Seq("col1")), MeasureResultDTO(TypedValue("1.2", ResultValueType.BigDecimal))
      ),
      MeasurementDTO(
        MeasureDTO("aggregatedTotal", Seq("col2")), MeasureResultDTO(TypedValue("1.3", ResultValueType.BigDecimal))
      )
    )

    assert(measurementDTOs == expectedMeasurementDTO)
  }

  "buildMeasurementDTO" should "throw exception for multiple measures, some of them repetitive" in {
    val measurements = Seq(
      Measurement(DistinctRecordCount("col"), MeasureResult("1")),
      Measurement(SumOfValuesOfColumn("col"), MeasureResult(BigDecimal(1.2))),
      Measurement(SumOfValuesOfColumn("col"), MeasureResult(BigDecimal(1.3)))
    )
    assertThrows[IllegalArgumentException](MeasurementBuilder.buildMeasurementDTO(measurements))
  }
}

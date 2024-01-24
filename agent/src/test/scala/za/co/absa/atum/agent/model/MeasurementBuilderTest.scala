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
import za.co.absa.atum.agent.exception.AtumAgentException.MeasurementException
import za.co.absa.atum.model.dto.{MeasureDTO, MeasureResultDTO, MeasurementDTO}
import za.co.absa.atum.agent.model.AtumMeasure._
import za.co.absa.atum.agent.model.MeasurementBuilder.validateMeasureUniqueness
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}

class MeasurementBuilderTest extends AnyFlatSpec {

  "validateMeasureUniqueness" should "accept unique measurements with unique measures" in {
    val measurements = Set(
      Measurement(RecordCount(), MeasureResult("1", ResultValueType.Long)),
      Measurement(SumOfValuesOfColumn("col1"), MeasureResult(BigDecimal(1.2))),
      Measurement(DistinctRecordCount(Seq("col2")), MeasureResult(3L))
    )
    validateMeasureUniqueness(measurements)
  }

  "validateMeasureUniqueness" should "accept duplicated measures but with different measured columns" in {
    val measurements = Set(
      Measurement(RecordCount(), MeasureResult("1", ResultValueType.Long)),
      Measurement(SumOfValuesOfColumn("col1"), MeasureResult(BigDecimal(1.2))),
      Measurement(SumOfValuesOfColumn("col2"), MeasureResult(BigDecimal(1.4))),
      Measurement(DistinctRecordCount(Seq("col2")), MeasureResult(3L))
    )
    validateMeasureUniqueness(measurements)
  }

  "validateMeasureUniqueness" should "fail on duplicated measures with repeated measured columns" in {
    val measurements = Set(
      Measurement(RecordCount(), MeasureResult("1", ResultValueType.Long)),
      Measurement(SumOfValuesOfColumn("col1"), MeasureResult(BigDecimal(1.2))),
      Measurement(SumOfValuesOfColumn("col1"), MeasureResult(BigDecimal(1.4))),
      Measurement(DistinctRecordCount(Seq("col2")), MeasureResult(3L))
    )
    assertThrows[MeasurementException](validateMeasureUniqueness(measurements))
  }

  "validateMeasureUniqueness" should "fail on duplicated measurements with different results" in {
    val measurements = Set(
      Measurement(RecordCount(), MeasureResult("1", ResultValueType.Long)),
      Measurement(RecordCount(), MeasureResult("2", ResultValueType.Long))
    )
    assertThrows[MeasurementException](validateMeasureUniqueness(measurements))
  }

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


  "buildMeasurementDTO" should "build Seq[MeasurementDTO] for multiple measures, all unique" in {
    val measurements = Set(
      Measurement(DistinctRecordCount(Seq("col")), MeasureResult("1", ResultValueType.Long)),
      Measurement(SumOfValuesOfColumn("col1"), MeasureResult(BigDecimal(1.2))),
      Measurement(SumOfValuesOfColumn("col2"), MeasureResult(BigDecimal(1.3)))
    )
    val measurementDTOs = MeasurementBuilder.buildMeasurementDTO(measurements)

    val expectedMeasurementDTO = Set(
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
    val measurements = Set(
      Measurement(DistinctRecordCount(Seq("col")), MeasureResult(1L)),
      Measurement(SumOfValuesOfColumn("col"), MeasureResult(BigDecimal(1.2))),
      Measurement(SumOfValuesOfColumn("col"), MeasureResult(BigDecimal(1.3)))
    )
    assertThrows[MeasurementException](MeasurementBuilder.buildMeasurementDTO(measurements))
  }
}

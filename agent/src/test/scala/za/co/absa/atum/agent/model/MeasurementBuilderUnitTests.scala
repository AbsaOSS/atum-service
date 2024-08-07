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
import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue

class MeasurementBuilderUnitTests extends AnyFlatSpec {

  "buildMeasurementDTO" should
    "build MeasurementDTO for BigDecimal type of result value when Measure and MeasureResult provided" in {

    val measure = SumOfValuesOfColumn("col")
    val measureResult = MeasureResult(BigDecimal(1))

    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measure, measureResult)

    val expectedMeasureDTO = MeasureDTO("aggregatedTotal", Seq("col"))

    val expectedMeasureResultDTO = MeasureResultDTO(
      TypedValue("1", ResultValueType.BigDecimalValue)
    )

    assert(measurementDTO.measure == expectedMeasureDTO)
    assert(measurementDTO.result == expectedMeasureResultDTO)
  }

  "buildMeasurementDTO" should
    "build MeasurementDTO for BigDecimal type of result value when Measurement provided" in {

    val measure = SumOfValuesOfColumn("col")
    val measureResult = MeasureResult(BigDecimal(3.14))
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measure, measureResult)

    val expectedTypedValue = TypedValue("3.14", ResultValueType.BigDecimalValue)

    assert(measurementDTO.result.mainValue == expectedTypedValue)
  }

  "buildMeasurementDTO" should
    "build MeasurementDTO (at least for now) for compatible result type but incompatible actual type of result value " +
      "when Measurement provided" in {

    val measure = SumOfValuesOfColumn("col")
    val measureResult = MeasureResult("string", ResultValueType.BigDecimalValue)

    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measure, measureResult)

    val expectedTypedValue = TypedValue("string", ResultValueType.BigDecimalValue)

    assert(measurementDTO.result.mainValue == expectedTypedValue)
  }

  "buildMeasurementDTO" should
    "build MeasurementDTO for BigDecimal type of result value when measured by Agent" in {

    val measure = SumOfValuesOfColumn("col")
    val measureResult = MeasureResult("1", ResultValueType.BigDecimalValue)

    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measure, measureResult)

    val expectedMeasureDTO = MeasureDTO("aggregatedTotal", Seq("col"))

    val expectedMeasureResultDTO = MeasureResultDTO(
      TypedValue("1", ResultValueType.BigDecimalValue)
    )

    assert(measurementDTO.measure == expectedMeasureDTO)
    assert(measurementDTO.result == expectedMeasureResultDTO)
  }

  "buildAndValidateMeasurementsDTO" should "build Seq[MeasurementDTO] for multiple measures, all unique" in {
    val measurements: Map[Measure, MeasureResult] = Map(
      DistinctRecordCount(Seq("col")) -> MeasureResult("1", ResultValueType.LongValue),
      SumOfValuesOfColumn("col1")     -> MeasureResult(BigDecimal(1.2)),
      SumOfValuesOfColumn("col2")     -> MeasureResult(BigDecimal(1.3)),
      UnknownMeasure("unknownMeasure", Seq("col"), ResultValueType.BigDecimalValue) -> MeasureResult(BigDecimal(1.1))
    )
    val measurementDTOs = MeasurementBuilder.buildAndValidateMeasurementsDTO(measurements)

    val expectedMeasurementDTO = Set(
      MeasurementDTO(
        MeasureDTO("distinctCount", Seq("col")), MeasureResultDTO(TypedValue("1", ResultValueType.LongValue))
      ),
      MeasurementDTO(
        MeasureDTO("aggregatedTotal", Seq("col1")), MeasureResultDTO(TypedValue("1.2", ResultValueType.BigDecimalValue))
      ),
      MeasurementDTO(
        MeasureDTO("aggregatedTotal", Seq("col2")), MeasureResultDTO(TypedValue("1.3", ResultValueType.BigDecimalValue))
      ),
      MeasurementDTO(
        MeasureDTO("unknownMeasure", Seq("col")), MeasureResultDTO(TypedValue("1.1", ResultValueType.BigDecimalValue))
      )
    )

    assert(measurementDTOs == expectedMeasurementDTO)
  }

  "buildAndValidateMeasurementsDTO" should "throw exception for unsupported result value - Double instead of BigDecimal" in {
    val measure = AbsSumOfValuesOfColumn("col")

    assertThrows[MeasurementException](
      MeasurementBuilder.buildAndValidateMeasurementsDTO(Map(measure -> MeasureResult(1.0)))
    )
  }

  "buildAndValidateMeasurementsDTO" should "throw exception for unsupported result value - Int instead of BigDecimal" in {
    val measure = SumOfValuesOfColumn("col")

    assertThrows[MeasurementException](
      MeasurementBuilder.buildAndValidateMeasurementsDTO(Map(measure -> MeasureResult(1)))
    )
  }

  "buildAndValidateMeasurementsDTO" should "throw exception for unsupported result value type in general (composite)" in {
    val measure = SumOfHashesOfColumn("col")

    assertThrows[MeasurementException](
      MeasurementBuilder.buildAndValidateMeasurementsDTO(Map(measure -> MeasureResult(Map(1 -> "no-go"))))
    )
  }

  "buildAndValidateMeasurementsDTO" should "throw exception for unsupported result value type for a given Measure" in {
    val measure = DistinctRecordCount(Seq("col"))

    assertThrows[MeasurementException](
      MeasurementBuilder.buildAndValidateMeasurementsDTO(Map(measure -> MeasureResult("1")))
    )
  }

  "buildAndValidateMeasurementsDTO" should "throw exception for incompatible String type of result value when Measurement provided" in {
    val measure = SumOfValuesOfColumn("col")

    assertThrows[MeasurementException](
      MeasurementBuilder.buildAndValidateMeasurementsDTO(Map(measure -> MeasureResult("string", ResultValueType.StringValue)))
    )
  }
}

package za.co.absa.atum.agent.model

import org.scalatest.flatspec.AnyFlatSpec
import za.co.absa.atum.agent.exception.UnsupportedMeasureResultType
import za.co.absa.atum.agent.model.Measure.SumOfValuesOfColumn
import za.co.absa.atum.model.dto.{MeasureDTO, MeasureResultDTO}
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}

class MeasurementBuilderTest extends AnyFlatSpec {

  "buildMeasurementDTO" should "build expected MeasurementDTO for Long type of result value" in {
    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, 1L)
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedMeasureDTO = MeasureDTO("aggregatedTotal", Seq("col"))

    val expectedMeasureResultDTO = MeasureResultDTO(
      TypedValue("1", ResultValueType.Long)
    )

    assert(measurementDTO.measure == expectedMeasureDTO)
    assert(measurementDTO.result == expectedMeasureResultDTO)
  }

  "buildMeasurementDTO" should "build MeasurementDTO with expected TypedValue for Double type of result value" in {
    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, 3.14)
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedTypedValue = TypedValue("3.14", ResultValueType.Double)

    assert(measurementDTO.result.mainValue == expectedTypedValue)
  }

  "buildMeasurementDTO" should "build MeasurementDTO with expected TypedValue for BigDecimal type of result value" in {
    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, BigDecimal(3.14))
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedTypedValue = TypedValue("3.14", ResultValueType.BigDecimal)

    assert(measurementDTO.result.mainValue == expectedTypedValue)
  }

  "buildMeasurementDTO" should "build MeasurementDTO with expected TypedValue for String type of result value" in {
    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, "stringValue")
    val measurementDTO = MeasurementBuilder.buildMeasurementDTO(measurement)

    val expectedTypedValue = TypedValue("stringValue", ResultValueType.String)

    assert(measurementDTO.result.mainValue == expectedTypedValue)
  }

  "buildMeasurementDTO" should "throw exception for unsupported result value type" in {
    val measure = SumOfValuesOfColumn("col")
    val measurement = Measurement(measure, 1)

    assertThrows[UnsupportedMeasureResultType](MeasurementBuilder.buildMeasurementDTO(measurement))
  }

}

package za.co.absa.atum.agent.model

import za.co.absa.atum.model.dto.{MeasureDTO, MeasureResultDTO, MeasurementDTO}
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}

object MeasurementBuilder {

  def buildLongMeasurement(functionName: String, controlCols: Seq[String], resultValue: Long): MeasurementDTO = {
    MeasurementDTO(
      MeasureDTO(functionName, controlCols),
      MeasureResultDTO(TypedValue(resultValue.toString, ResultValueType.Long))
    )
  }

  def buildDoubleMeasureResult(functionName: String, controlCols: Seq[String], resultValue: Double): MeasurementDTO = {
    MeasurementDTO(
      MeasureDTO(functionName, controlCols),
      MeasureResultDTO(TypedValue(resultValue.toString, ResultValueType.Double))
    )
  }

  def buildBigDecimalMeasureResult(functionName: String, controlCols: Seq[String], resultValue: BigDecimal): MeasurementDTO = {
    MeasurementDTO(
      MeasureDTO(functionName, controlCols),
      MeasureResultDTO(TypedValue(resultValue.toString, ResultValueType.BigDecimal))
    )
  }

  def buildStringMeasureResult(functionName: String, controlCols: Seq[String], resultValue: String): MeasurementDTO = {
    MeasurementDTO(
      MeasureDTO(functionName, controlCols),
      MeasureResultDTO(TypedValue(resultValue, ResultValueType.String))
    )
  }

}

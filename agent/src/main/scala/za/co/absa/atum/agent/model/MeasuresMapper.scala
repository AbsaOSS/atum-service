package za.co.absa.atum.agent.model

import za.co.absa.atum.agent.model.Measure._

case class UnsupportedMeasureException(msg: String) extends Exception(msg)

object MeasuresMapper {

  def mapToMeasures(measures: Set[za.co.absa.atum.model.Measure]): Set[za.co.absa.atum.agent.model.Measure] = {
    measures.map(createMeasure)
  }

  private def createMeasure(measure: za.co.absa.atum.model.Measure): za.co.absa.atum.agent.model.Measure = {
    val controlColumn = measure.controlColumns.head
    measure.functionName match {
      case "RecordCount" => RecordCount(controlColumn)
      case "DistinctRecordCount"    => DistinctRecordCount(controlColumn)
      case "SumOfValuesOfColumn"    => SumOfValuesOfColumn(controlColumn)
      case "AbsSumOfValuesOfColumn" => AbsSumOfValuesOfColumn(controlColumn)
      case "SumOfHashesOfColumn"    => SumOfHashesOfColumn(controlColumn)
      case unsupportedMeasure       => throw UnsupportedMeasureException(s"Measure not supported: $unsupportedMeasure")
    }
  }

}

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

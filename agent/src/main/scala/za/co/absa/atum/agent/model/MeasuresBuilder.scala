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

import za.co.absa.atum.agent.exception.MeasureException
import za.co.absa.atum.agent.model.AtumMeasure._
import za.co.absa.atum.model.dto.MeasureDTO

private [agent] object MeasuresBuilder {

  private [agent] def mapToMeasures(measures: Set[MeasureDTO]): Set[za.co.absa.atum.agent.model.AtumMeasure] = {
    measures.map(createMeasure)
  }

  private def createMeasure(measure: MeasureDTO): za.co.absa.atum.agent.model.AtumMeasure = {
    val controlColumns = measure.controlColumns

    measure.measureName match {
      case RecordCount.measureName            => RecordCount()
      case DistinctRecordCount.measureName    => DistinctRecordCount(controlColumns)
      case SumOfValuesOfColumn.measureName    => SumOfValuesOfColumn(controlColumns.head)
      case AbsSumOfValuesOfColumn.measureName => AbsSumOfValuesOfColumn(controlColumns.head)
      case SumOfHashesOfColumn.measureName    => SumOfHashesOfColumn(controlColumns.head)
      case unsupportedMeasure =>
        throw MeasureException(
          s"Measure not supported: $unsupportedMeasure. Supported measures are: ${AtumMeasure.supportedMeasureNames}"
        )
    }
  }

}

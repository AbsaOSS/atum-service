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

import za.co.absa.atum.agent.exception.AtumAgentException.MeasureException
import za.co.absa.atum.agent.model.Measure._
import za.co.absa.atum.model.dto

/**
 * This object provides a functionality to convert a DTO representation of measures to their internal representation.
 */
private [agent] object MeasuresBuilder {

  private [agent] def mapToMeasures(measures: Set[dto.MeasureDTO]): Set[za.co.absa.atum.agent.model.Measure] = {
    measures.map(createMeasure)
  }

  private def createMeasure(measure: dto.MeasureDTO): za.co.absa.atum.agent.model.Measure = {
    val measuredColumn = measure.measuredColumns.head

    measure.measureName match {
      case RecordCount.measureName            => RecordCount(measuredColumn)
      case DistinctRecordCount.measureName    => DistinctRecordCount(measuredColumn)
      case SumOfValuesOfColumn.measureName    => SumOfValuesOfColumn(measuredColumn)
      case AbsSumOfValuesOfColumn.measureName => AbsSumOfValuesOfColumn(measuredColumn)
      case SumOfHashesOfColumn.measureName    => SumOfHashesOfColumn(measuredColumn)
      case unsupportedMeasure =>
        throw MeasureException(
          s"Measure not supported: $unsupportedMeasure. Supported measures are: ${Measure.supportedMeasureNames}"
        )
    }
  }

}

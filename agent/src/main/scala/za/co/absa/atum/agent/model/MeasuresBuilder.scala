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

import za.co.absa.atum.agent.model.AtumMeasure._
import za.co.absa.atum.model.dto.MeasureDTO
import org.apache.spark.internal.Logging

import scala.util.Try

/**
 * This object provides a functionality to convert a DTO representation of measures to the Agent's internal
 * representation of those objects.
 */
private [agent] object MeasuresBuilder extends Logging {

  private [agent] def mapToMeasures(measures: Set[MeasureDTO]): Set[za.co.absa.atum.agent.model.AtumMeasure] = {
    measures.flatMap { measure =>
      createMeasure(measure) match {
        case Some(value) =>
          Some(value)
        case None =>
          logWarning(s"Measure not supported or unknown: $measure.")
          None
      }
    }
  }

  private def createMeasure(measure: MeasureDTO): Option[za.co.absa.atum.agent.model.AtumMeasure] = {
    val measuredColumns = measure.measuredColumns

    Try {
      measure.measureName match {
        case RecordCount.measureName => RecordCount()
        case DistinctRecordCount.measureName => DistinctRecordCount(measuredColumns)
        case SumOfValuesOfColumn.measureName => SumOfValuesOfColumn(measuredColumns.head)
        case AbsSumOfValuesOfColumn.measureName => AbsSumOfValuesOfColumn(measuredColumns.head)
        case SumOfHashesOfColumn.measureName => SumOfHashesOfColumn(measuredColumns.head)
      }
    }.toOption
  }

}

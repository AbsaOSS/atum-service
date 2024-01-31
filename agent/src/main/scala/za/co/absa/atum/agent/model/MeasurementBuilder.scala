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

import za.co.absa.atum.agent.exception.AtumAgentException.MeasurementException
import za.co.absa.atum.model.dto.{MeasureDTO, MeasureResultDTO, MeasurementDTO}

/**
 * This object provides a functionality to convert a measurement to its DTO representation.
 */
private [agent] object MeasurementBuilder {

  private[agent] def validateMeasureUniqueness(measurements: Set[Measurement]): Unit = {
    val allMeasures = measurements.toSeq.map(_.measure)

    val originalMeasuresCnt = allMeasures.size
    val uniqueMeasureColumnCombinationCnt = allMeasures.map(m =>
      (m.measureName, m.measuredColumns)  // there can't be two same measures defined on the same column(s)
    ).distinct.size

    if (originalMeasuresCnt != uniqueMeasureColumnCombinationCnt)
      throw MeasurementException(
        s"Measure and measuredColumn combinations must be unique, i.e. they cannot repeat! Got: $allMeasures"
      )
  }

  private[agent] def buildMeasurementsDTO(measurements: Set[Measurement]): Set[MeasurementDTO] = {
    validateMeasureUniqueness(measurements)

    measurements.map(m => buildMeasurementDTO(m.measure, m.result))
  }

  private[agent] def buildMeasurementDTO(measure: AtumMeasure, measureResult: MeasureResult): MeasurementDTO = {
    val measureDTO = MeasureDTO(measure.measureName, measure.measuredColumns)

    val measureResultDTO = MeasureResultDTO(
      MeasureResultDTO.TypedValue(measureResult.resultValue.toString, measureResult.resultValueType)
    )
    MeasurementDTO(measureDTO, measureResultDTO)
  }
}

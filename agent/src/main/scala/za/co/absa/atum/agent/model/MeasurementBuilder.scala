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

  private def validateMeasureAndResultTypeCompatibility(measure: Measure, result: MeasureResult): Unit = {
    val requiredType = measure.resultValueType
    val actualType = result.resultValueType

    if (actualType != requiredType)
      throw MeasurementException(
        s"Type of a given provided measurement result and type that a given measure supports are not compatible! " +
          s"Got $actualType but should be $requiredType"
      )
  }

  def buildMeasurementDTO(measure: Measure, measureResult: MeasureResult): MeasurementDTO = {
    validateMeasureAndResultTypeCompatibility(measure, measureResult)

    val measureDTO = MeasureDTO(measure.measureName, measure.measuredColumns)

    val measureResultDTO = MeasureResultDTO(
      MeasureResultDTO.TypedValue(measureResult.resultValue.toString, measureResult.resultValueType)
    )
    MeasurementDTO(measureDTO, measureResultDTO)
  }

  def buildAndValidateMeasurementsDTO(measurements: Map[Measure, MeasureResult]): Set[MeasurementDTO] = {
    measurements.toSet[(Measure, MeasureResult)].map { case (measure: Measure, measureResult: MeasureResult) =>
        validateMeasureAndResultTypeCompatibility(measure, measureResult)
        buildMeasurementDTO(measure, measureResult)
    }
  }

}

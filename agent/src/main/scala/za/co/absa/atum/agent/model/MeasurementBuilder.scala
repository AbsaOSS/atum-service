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

private [agent] object MeasurementBuilder {

  private def validateMeasurement(measure: Measure, result: MeasureResult): Unit = {
    val actualType = result.resultType
    val requiredType = measure.resultValueType

    if (actualType != requiredType)
      throw MeasurementException(
        s"Type of a given provided measurement result and type that a given measure supports are not compatible! " +
          s"Got $actualType but should be $requiredType"
      )
  }

  private def validateMeasurementUniqueness(measurements: Set[Measurement]): Unit = {
    val originalMeasurementsCnt = measurements.size
    val uniqueMeasuresCnt = measurements.toSeq.map(m =>
      Tuple2(m.measure.measureName, m.measure.measuredColumn)  // there can't be 2 same measures defined on the same column
    ).distinct.size

    val areMeasuresUnique = originalMeasurementsCnt == uniqueMeasuresCnt

    require(areMeasuresUnique, s"Measures must be unique, i.e. they cannot repeat! Got: ${measurements.map(_.measure)}")
  }

  private[agent] def buildMeasurementDTO(measurements: Set[Measurement]): Set[MeasurementDTO] = {
    validateMeasurementUniqueness(measurements)

    measurements.map(m => buildMeasurementDTO(m.measure, m.result))
  }

  private[agent] def buildMeasurementDTO(measurement: Measurement): MeasurementDTO = {
    buildMeasurementDTO(measurement.measure, measurement.result)
  }

  private[agent] def buildMeasurementDTO(measure: Measure, measureResult: MeasureResult): MeasurementDTO = {
    val measureName = measure.measureName
    val measuredColumns = Seq(measure.measuredColumn)

    validateMeasurement(measure, measureResult)

    val measureDTO = MeasureDTO(measureName, measuredColumns)
    val measureResultDTO = MeasureResultDTO(
      MeasureResultDTO.TypedValue(measureResult.resultValue.toString, measureResult.resultType)
    )
    MeasurementDTO(measureDTO, measureResultDTO)
  }
}

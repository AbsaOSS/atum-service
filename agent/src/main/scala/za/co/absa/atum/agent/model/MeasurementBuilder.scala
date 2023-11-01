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

import za.co.absa.atum.agent.exception.MeasurementException
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

  private def validateMeasuresUniqueness(measures: Seq[Measure]): Unit = {
    val originalMeasureCnt = measures.size
    val uniqueMeasuresCnt = measures.map(m => Tuple2(m.measureName, m.controlCol)).distinct.size

    val areMeasuresUnique = originalMeasureCnt == uniqueMeasuresCnt

    require(areMeasuresUnique, s"Measures must be unique, i.e. they cannot repeat! Got: $measures")
  }

  private[agent] def buildMeasurementDTO(measurements: Seq[Measurement]): Seq[MeasurementDTO] = {
    val allMeasures = measurements.map(_.measure)
    validateMeasuresUniqueness(allMeasures)

    measurements.map(m => buildMeasurementDTO(m.measure, m.result))
  }

  private[agent] def buildMeasurementDTO(measurement: Measurement): MeasurementDTO = {
    buildMeasurementDTO(measurement.measure, measurement.result)
  }

  private[agent] def buildMeasurementDTO(measure: Measure, measureResult: MeasureResult): MeasurementDTO = {
    val measureName = measure.measureName
    val controlCols = Seq(measure.controlCol)

    validateMeasurement(measure, measureResult)

    val measureDTO = MeasureDTO(measureName, controlCols)
    val measureResultDTO = MeasureResultDTO(
      MeasureResultDTO.TypedValue(measureResult.resultValue.toString, measureResult.resultType)
    )
    MeasurementDTO(measureDTO, measureResultDTO)
  }
}

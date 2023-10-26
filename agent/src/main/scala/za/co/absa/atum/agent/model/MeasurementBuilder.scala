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

import za.co.absa.atum.agent.exception.UnsupportedMeasureResultType
import za.co.absa.atum.model.dto.{MeasureDTO, MeasureResultDTO, MeasurementDTO}
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}

private [agent] object MeasurementBuilder {

  private [agent] def buildMeasurementDTO(measurement: Measurement): MeasurementDTO = {
    val measureName = measurement.measure.measureName
    val controlCols = Seq(measurement.measure.controlCol)
    val measureDTO = MeasureDTO(measureName, controlCols)

    val measureResultDTO = measurement match {
      case m: MeasurementByAtum =>
        MeasureResultDTO(TypedValue(m.result, m.resultType))
      case m: Measurement =>
        buildMeasureResultDTO(measureName, m.result)
    }

    MeasurementDTO(measureDTO, measureResultDTO)
  }

  private [agent] def buildMeasureResultDTO(measureName: String, result: Any): MeasureResultDTO = {
    result match {
      case l: Long =>
        MeasureResultDTO(TypedValue(l.toString, ResultValueType.Long))
      case d: Double =>
        MeasureResultDTO(TypedValue(d.toString, ResultValueType.Double))
      case bd: BigDecimal =>
        MeasureResultDTO(TypedValue(bd.toString, ResultValueType.BigDecimal))
      case s: String =>
        MeasureResultDTO(TypedValue(s, ResultValueType.String))
      case unsupportedType =>
        val className = unsupportedType.getClass.getSimpleName
        throw UnsupportedMeasureResultType(s"Unsupported type of measure $measureName: $className for result: $result")
    }
  }

}

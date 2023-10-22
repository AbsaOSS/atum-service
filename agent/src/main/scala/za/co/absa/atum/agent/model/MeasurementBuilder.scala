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

import za.co.absa.atum.model.dto.{MeasureDTO, MeasureResultDTO, MeasurementDTO}
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}

object MeasurementBuilder {

  def buildLongMeasurement(functionName: String, controlCols: Seq[String], resultValue: Long): MeasurementDTO = {
    MeasurementDTO(
      MeasureDTO(functionName, controlCols),
      MeasureResultDTO(TypedValue(resultValue.toString, ResultValueType.Long))
    )
  }

  def buildDoubleMeasureResult(functionName: String, controlCols: Seq[String], resultValue: Double): MeasurementDTO = {
    MeasurementDTO(
      MeasureDTO(functionName, controlCols),
      MeasureResultDTO(TypedValue(resultValue.toString, ResultValueType.Double))
    )
  }

  def buildBigDecimalMeasureResult(functionName: String, controlCols: Seq[String], resultValue: BigDecimal): MeasurementDTO = {
    MeasurementDTO(
      MeasureDTO(functionName, controlCols),
      MeasureResultDTO(TypedValue(resultValue.toString, ResultValueType.BigDecimal))
    )
  }

  def buildStringMeasureResult(functionName: String, controlCols: Seq[String], resultValue: String): MeasurementDTO = {
    MeasurementDTO(
      MeasureDTO(functionName, controlCols),
      MeasureResultDTO(TypedValue(resultValue, ResultValueType.String))
    )
  }

}

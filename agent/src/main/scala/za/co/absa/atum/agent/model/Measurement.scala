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
import za.co.absa.atum.agent.model.Measurement.validateMeasurement

/**
 *  This class defines a contract for a measurement.
 */
final case class Measurement private (measure: AtumMeasure, result: MeasureResult) {
  validateMeasurement(measure, result)
}

object Measurement {

  private def validateMeasurement(measure: AtumMeasure, result: MeasureResult): Unit = {
    val actualType = result.resultType
    val requiredType = measure.resultValueType

    if (actualType != requiredType)
      throw MeasurementException(
        s"Type of a given provided measurement result and type that a given measure supports are not compatible! " +
          s"Got $actualType but should be $requiredType"
      )
  }
}

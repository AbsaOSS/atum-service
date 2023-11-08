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

import za.co.absa.atum.agent.exception.MeasurementProvidedException
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType

trait Measurement {
  val measure: Measure
  val resultValue: Any
  val resultType: ResultValueType.ResultValueType
}

object Measurement {

  /**
   *  When the application/user of Atum Agent provides actual results by himself, the type is precise and we don't need
   *  to do any adjustments.
   */
  case class MeasurementProvided[T](measure: Measure, resultValue: T, resultType: ResultValueType.ResultValueType)
      extends Measurement

  object MeasurementProvided {

    private def handleSpecificType[T](
      measure: AtumMeasure,
      resultValue: T,
      requiredType: ResultValueType.ResultValueType
    ): MeasurementProvided[T] = {

      val actualType = measure.resultValueType
      if (actualType != requiredType)
        throw MeasurementProvidedException(
          s"Type of a given provided measurement result and type that a given measure supports are not compatible! " +
            s"Got $actualType but should be $requiredType"
        )
      MeasurementProvided[T](measure, resultValue, requiredType)
    }

    def apply[T](measure: AtumMeasure, resultValue: T): Measurement = {
      resultValue match {
        case l: Long =>
          handleSpecificType[Long](measure, l, ResultValueType.Long)
        case d: Double =>
          handleSpecificType[Double](measure, d, ResultValueType.Double)
        case bd: BigDecimal =>
          handleSpecificType[BigDecimal](measure, bd, ResultValueType.BigDecimal)
        case s: String =>
          handleSpecificType[String](measure, s, ResultValueType.String)

        case unsupportedType =>
          val className = unsupportedType.getClass.getSimpleName
          throw MeasurementProvidedException(
            s"Unsupported type of measurement for measure ${measure.measureName}: $className " +
              s"for provided result: $resultValue"
          )
      }
    }

    def forCustomMeasure[T](measureName: String, controlCol: String, resultValue: T): MeasurementProvided[T] = {
      resultValue match {
        case _: Long =>
          MeasurementProvided[T](CustomMeasure(measureName, controlCol), resultValue, ResultValueType.Long)
        case _: Double =>
          MeasurementProvided[T](CustomMeasure(measureName, controlCol), resultValue, ResultValueType.Double)
        case _: BigDecimal =>
          MeasurementProvided[T](CustomMeasure(measureName, controlCol), resultValue, ResultValueType.BigDecimal)
        case _: String =>
          MeasurementProvided[T](CustomMeasure(measureName, controlCol), resultValue, ResultValueType.String)
        case unsupportedType =>
          val className = unsupportedType.getClass.getSimpleName
          throw MeasurementProvidedException(
            s"Unsupported type of measurement for measure ${measureName}: $className " +
              s"for provided result: $resultValue"
          )
      }
    }

    /**
     *  When the Atum Agent itself performs the measurements, using Spark, then in some cases some adjustments are
     *  needed - thus we are converting the results to strings always - but we need to keep the information about
     *  the actual type as well.
     */
    case class MeasurementByAtum(measure: AtumMeasure, resultValue: String, resultType: ResultValueType.ResultValueType)
        extends Measurement
  }
}

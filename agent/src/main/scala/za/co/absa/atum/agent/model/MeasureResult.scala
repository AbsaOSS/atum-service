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
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType

trait MeasureResult {
  val resultValue: Any
  val resultType: ResultValueType.ResultValueType
}

object MeasureResult {
  private final case class MeasureResultWithType[T](resultValue: T, resultType: ResultValueType.ResultValueType)
    extends MeasureResult

  /**
   * When the Atum Agent itself performs the measurements, using Spark, then in some cases some adjustments are
   * needed - thus we are converting the results to strings always - but we need to keep the information about
   * the actual type as well.
   *
   * These adjustments are needed to be performed - to avoid some floating point issues
   * (overflows, consistent representation of numbers - whether they are coming from Java or Scala world, and more).
   */
  def apply(resultValue: String, resultType: ResultValueType.ResultValueType): MeasureResult = {
    MeasureResultWithType[String](resultValue, resultType)
  }

  /**
   * When the application/user of Atum Agent provides actual results by himself, the type is precise and we don't need
   * to do any adjustments.
   */
  def apply(resultValue: Any): MeasureResult = {
    resultValue match {

      case l: Long =>
        MeasureResultWithType[Long](l, ResultValueType.Long)
      case d: Double =>
        MeasureResultWithType[Double](d, ResultValueType.Double)
      case bd: BigDecimal =>
        MeasureResultWithType[BigDecimal](bd, ResultValueType.BigDecimal)
      case s: String =>
        MeasureResultWithType[String](s, ResultValueType.String)

      case unsupportedType =>
        val className = unsupportedType.getClass.getSimpleName
        throw MeasurementException(
          s"Unsupported type of measurement: $className for provided result: $resultValue")
    }
  }
}

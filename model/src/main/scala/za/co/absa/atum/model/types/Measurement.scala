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

package za.co.absa.atum.model.types

import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.MeasurementDTO

trait Measurement {
  type T
  def measureName: String
  def measuredColumns: Seq[String]
  def valueType: ResultValueType
  def value: T
  def stringValue: String
}

object Measurement {

  def apply[T](from: MeasurementDTO): Measurement = {
    from.result.mainValue.valueType match {
      case ResultValueType.StringValue => StringMeasurement(from.measure.measureName, from.measure.measuredColumns, from.result.mainValue.value)
      case ResultValueType.LongValue => LongMeasurement(from.measure.measureName, from.measure.measuredColumns, from.result.mainValue.value.toLong)
      case ResultValueType.BigDecimalValue => BigDecimalMeasurement(from.measure.measureName, from.measure.measuredColumns, BigDecimal(from.result.mainValue.value))
      case ResultValueType.DoubleValue => DoubleMeasurement(from.measure.measureName, from.measure.measuredColumns, from.result.mainValue.value.toDouble)
    }
  }

  case class StringMeasurement(
                                measureName: String,
                                measuredColumns: Seq[String],
                                value: String
                              ) extends Measurement {
    override type T = String
    override def valueType: ResultValueType = ResultValueType.StringValue
    override def stringValue: String = value
  }

  case class LongMeasurement(
    measureName: String,
    measuredColumns: Seq[String],
    value: Long
  ) extends Measurement {
    override type T = Long
    override def valueType: ResultValueType = ResultValueType.LongValue
    override def stringValue: String = value.toString
  }

  case class BigDecimalMeasurement(
    measureName: String,
    measuredColumns: Seq[String],
    value: BigDecimal
  ) extends Measurement {
    override type T = BigDecimal
    override def valueType: ResultValueType = ResultValueType.BigDecimalValue
    override def stringValue: String = value.toString
  }

  case class DoubleMeasurement(
    measureName: String,
    measuredColumns: Seq[String],
    value: Double
  ) extends Measurement {
    override type T = Double
    override def valueType: ResultValueType = ResultValueType.DoubleValue
    override def stringValue: String = value.toString
  }

}

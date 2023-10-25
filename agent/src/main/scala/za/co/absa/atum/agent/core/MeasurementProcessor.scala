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

package za.co.absa.atum.agent.core

import org.apache.spark.sql.DataFrame
import za.co.absa.atum.agent.core.MeasurementProcessor.MeasurementFunction
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType

trait MeasurementProcessor {

  def function: MeasurementFunction

}

object MeasurementProcessor {
  /**
   * The raw result of measurement is always gonna be string, because we want to avoid some floating point issues
   * (overflows, consistent representation of numbers - whether they are coming from Java or Scala world, and more),
   * but the actual type is stored alongside the computation because we don't want to lost this information.
   */
  final case class ResultOfMeasurement(resultValue: String, resultType: ResultValueType.ResultValueType)

  type MeasurementFunction = DataFrame => ResultOfMeasurement

}

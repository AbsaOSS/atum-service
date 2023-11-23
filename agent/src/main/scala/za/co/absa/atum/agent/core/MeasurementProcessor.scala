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
import za.co.absa.atum.agent.model.MeasureResult

/**
 *  This trait provides a contract for different measurement processors
 */
trait MeasurementProcessor {

  /**
   *  This method is used to compute measure on Spark `Dataframe`.
   *  @param df: Spark `Dataframe` to be measured.
   *  @return Result of measurement.
   */
  def function: MeasurementFunction
}

/**
 *  This companion object provides a set of types for measurement processors
 */
object MeasurementProcessor {
  /**
   * This type alias describes a function that is used to compute measure on Spark `Dataframe`.
   * @param df: Spark `Dataframe` to be measured.
   * @return Result of measurement.
   */
  type MeasurementFunction = DataFrame => MeasureResult
}

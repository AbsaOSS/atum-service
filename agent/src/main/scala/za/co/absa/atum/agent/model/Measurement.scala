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

import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType.ResultValueType

trait Measurement {
  val measure: Measure
  val result: Any
}

/**
 * When the application/user of Atum Agent provides actual results by himself, the type is precise and we don't need
 * to do any adjustments.
 */
case class MeasurementProvided(measure: Measure, result: Any) extends Measurement

/**
 * When the Atum Agent itself performs the measurements, using Spark, then in some cases some adjustments are
 * needed - thus we are converting the results to strings always - but we need to keep the information about
 * the actual type as well.
 */
case class MeasurementByAtum(measure: Measure, result: String, resultType: ResultValueType) extends Measurement

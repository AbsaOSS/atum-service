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

package za.co.absa.atum.model.dto

import java.time.ZonedDateTime
import java.util.UUID

// TODO REMOVE
case class MeasureResultDTO1(
                             mainValue: MeasureResultDTO1.TypedValue1,
                             // TODO READ doobie Map[String, MeasureResultDTO1.TypedValue1]
                             supportValues: Map[String, String /*MeasureResultDTO1.TypedValue1*/] = Map.empty
                           )

object MeasureResultDTO1 {
  case class TypedValue1(
                         value: String,
                          // TODO READ doobie sealed trait ResultValueType
                         valueType: String//ResultValueType1
                       )

  sealed trait ResultValueType1

  object ResultValueType1 {
    case object String extends ResultValueType1
    case object Long extends ResultValueType1
    case object BigDecimal extends ResultValueType1
    case object Double extends ResultValueType1
  }

}

case class CheckpointQueryResultDTO(
  idCheckpoint: UUID,
  checkpointName: String,
  measureName: String,
  measureColumns: Seq[String],
  measurementValue: MeasureResultDTO1, // TODO MeasureResultDTO
  checkpointStartTime: ZonedDateTime,
  checkpointEndTime:  Option[ZonedDateTime],
)

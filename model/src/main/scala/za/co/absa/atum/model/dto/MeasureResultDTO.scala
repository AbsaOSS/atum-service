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

import io.circe._
import io.circe.generic.semiauto._
import za.co.absa.atum.model.ResultValueType

case class MeasureResultDTO(
 mainValue: MeasureResultDTO.TypedValue,
 supportValues: Map[String, MeasureResultDTO.TypedValue] = Map.empty
)

object MeasureResultDTO {
  case class TypedValue(
     value: String,
     valueType: ResultValueType
  )

  object TypedValue {
    implicit val encodeTypedValue: Encoder[TypedValue] = deriveEncoder
    implicit val decodeTypedValue: Decoder[TypedValue] = deriveDecoder
  }

  implicit val encodeMeasureResultDTO: Encoder[MeasureResultDTO] = deriveEncoder
  implicit val decodeMeasureResultDTO: Decoder[MeasureResultDTO] = deriveDecoder
}

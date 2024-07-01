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

sealed trait ResultValueType

object ResultValueType {
  case object String extends ResultValueType

  case object Long extends ResultValueType
  case object BigDecimal extends ResultValueType
  case object Double extends ResultValueType

  implicit val encodeResultValueType: Encoder[ResultValueType] = Encoder.encodeString.contramap {
    case ResultValueType.String => "String"
    case ResultValueType.Long => "Long"
    case ResultValueType.BigDecimal => "BigDecimal"
    case ResultValueType.Double => "Double"
  }

  implicit val decodeResultValueType: Decoder[ResultValueType] = Decoder.decodeString.emap {
    case "String" => Right(ResultValueType.String)
    case "Long" => Right(ResultValueType.Long)
    case "BigDecimal" => Right(ResultValueType.BigDecimal)
    case "Double" => Right(ResultValueType.Double)
    case other => Left(s"Cannot decode $other as ResultValueType")
  }

}
case class MeasureResultDTO(
  mainValue: TypedValue,
  supportValues: Map[String, TypedValue] = Map.empty
)

case class TypedValue(
                       value: String,
                       valueType: ResultValueType
                     )

object TypedValue {

  implicit val encodeTypedValue: Encoder[TypedValue] =
    Encoder.forProduct2("value", "valueType")(tv => (tv.value, tv.valueType))

  implicit val decodeTypedValue: Decoder[TypedValue] =
    Decoder.forProduct2("value", "valueType")(TypedValue.apply)

}
object MeasureResultDTO {

  implicit val decodeMeasureResultDTO: Decoder[MeasureResultDTO] =
    Decoder.forProduct2("mainValue", "supportValues")(MeasureResultDTO.apply)

}

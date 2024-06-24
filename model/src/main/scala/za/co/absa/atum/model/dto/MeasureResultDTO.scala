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

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._

case class MeasureResultDTO(
  mainValue: MeasureResultDTO.TypedValue,
  supportValues: Map[String, MeasureResultDTO.TypedValue] = Map.empty
)

object MeasureResultDTO {
  case class TypedValue(
    value: String,
    valueType: ResultValueType
  )

  sealed trait ResultValueType

  object ResultValueType {
    case object String extends ResultValueType
    case object Long extends ResultValueType
    case object BigDecimal extends ResultValueType
    case object Double extends ResultValueType
  }


  implicit val encodeResultValueType: Encoder[MeasureResultDTO.ResultValueType] = Encoder.encodeString.contramap {
    case MeasureResultDTO.ResultValueType.String      => "String"
    case MeasureResultDTO.ResultValueType.Long        => "Long"
    case MeasureResultDTO.ResultValueType.BigDecimal  => "BigDecimal"
    case MeasureResultDTO.ResultValueType.Double      => "Double"
  }

  implicit val decodeResultValueType: Decoder[MeasureResultDTO.ResultValueType] = Decoder.decodeString.emap {
    case "String"     => Right(MeasureResultDTO.ResultValueType.String)
    case "Long"       => Right(MeasureResultDTO.ResultValueType.Long)
    case "BigDecimal" => Right(MeasureResultDTO.ResultValueType.BigDecimal)
    case "Double"     => Right(MeasureResultDTO.ResultValueType.Double)
    case other        => Left(s"Cannot decode $other as ResultValueType")
  }

  implicit val encodeTypedValue: Encoder[MeasureResultDTO.TypedValue] =
    Encoder.forProduct2("value", "valueType")(tv => (tv.value, tv.valueType))

  implicit val decodeTypedValue: Decoder[MeasureResultDTO.TypedValue] =
    Decoder.forProduct2("value", "valueType")(MeasureResultDTO.TypedValue.apply)

  implicit val decodeMeasureResultDTO: Decoder[MeasureResultDTO] =
    Decoder.forProduct2("mainValue", "supportValues")(MeasureResultDTO.apply)

}

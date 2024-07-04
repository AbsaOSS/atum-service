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

package za.co.absa.atum.model

import io.circe.{Decoder, Encoder}

sealed trait ResultValueType

object ResultValueType {
  case object StringValue extends ResultValueType
  case object LongValue extends ResultValueType
  case object BigDecimalValue extends ResultValueType
  case object DoubleValue extends ResultValueType

  implicit val encodeResultValueType: Encoder[ResultValueType] = Encoder.encodeString.contramap {
    case ResultValueType.StringValue => "String"
    case ResultValueType.LongValue => "Long"
    case ResultValueType.BigDecimalValue => "BigDecimal"
    case ResultValueType.DoubleValue => "Double"
  }

  implicit val decodeResultValueType: Decoder[ResultValueType] = Decoder.decodeString.emap {
    case "String" => Right(ResultValueType.StringValue)
    case "Long" => Right(ResultValueType.LongValue)
    case "BigDecimal" => Right(ResultValueType.BigDecimalValue)
    case "Double" => Right(ResultValueType.DoubleValue)
    case other => Left(s"Cannot decode $other as ResultValueType")
  }
}

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

package za.co.absa.atum.model.utils


import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import io.circe.parser._
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

object SerializationUtils {


  val timestampFormat: DateTimeFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

  implicit val encodeZonedDateTime: Encoder[ZonedDateTime] = Encoder.encodeString.contramap[ZonedDateTime](_.format(timestampFormat))
  implicit val decodeZonedDateTime: Decoder[ZonedDateTime] = Decoder.decodeString.emap { str =>
    Right(ZonedDateTime.parse(str, timestampFormat))
  }

  implicit val encodeUUID: Encoder[UUID] = Encoder.encodeString.contramap[UUID](_.toString)
  implicit val decodeUUID: Decoder[UUID] = Decoder.decodeString.emap { str =>
    Right(UUID.fromString(str))
  }

  /**
   * The method returns arbitrary object as a Json string.
   *
   * @return A string representing the object in Json format
   */
  def asJson[T: Encoder](obj: T): String = {
    obj.asJson.noSpaces
  }

  /**
   * The method returns arbitrary object as a pretty Json string.
   *
   * @return A string representing the object in Json format
   */
  def asJsonPretty[T: Encoder](obj: T): String = {
    obj.asJson.spaces2
  }

  /**
   * The method returns arbitrary object parsed from Json string.
   *
   * @return An object deserialized from the Json string
   */
  def fromJson[T: Decoder](jsonStr: String): T = {
    decode[T](jsonStr) match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(s"Failed to decode JSON: $error")
    }
  }

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

}

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


//import io.circe.{Decoder, Encoder}
//import io.circe.generic.semiauto._
//import io.circe.syntax._
//import za.co.absa.atum.model.dto._
//
//import java.time.ZonedDateTime
//import java.time.format.DateTimeFormatter
//import java.util.UUID

//object JsonImplicits {
//
//  // Timestamp format
//  val timestampFormat: DateTimeFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME
//
//  // Implicit encoders and decoders for ZonedDateTime
//  implicit val encodeZonedDateTime: Encoder[ZonedDateTime] = Encoder.encodeString.contramap[ZonedDateTime](_.format(timestampFormat))
//  implicit val decodeZonedDateTime: Decoder[ZonedDateTime] = Decoder.decodeString.emap { str =>
//    Right(ZonedDateTime.parse(str, timestampFormat))
//  }
//
//  // Implicit encoders and decoders for UUID
//  implicit val encodeUUID: Encoder[UUID] = Encoder.encodeString.contramap[UUID](_.toString)
//  implicit val decodeUUID: Decoder[UUID] = Decoder.decodeString.emap { str =>
//    Right(UUID.fromString(str))
//  }
//
//  // Implicit encoders and decoders for Option[String]
//  implicit val decodeOptionString: Decoder[Option[String]] = Decoder.decodeOption[String]
//  implicit val encodeOptionString: Encoder[Option[String]] = Encoder.encodeOption[String]
//
//  // Implicit encoders and decoders for ResultValueType
//  sealed trait ResultValueType
//  object ResultValueType {
//    case object String extends ResultValueType
//    case object Long extends ResultValueType
//    case object BigDecimal extends ResultValueType
//    case object Double extends ResultValueType
//
//    implicit val encodeResultValueType: Encoder[ResultValueType] = Encoder.encodeString.contramap {
//      case ResultValueType.String => "String"
//      case ResultValueType.Long => "Long"
//      case ResultValueType.BigDecimal => "BigDecimal"
//      case ResultValueType.Double => "Double"
//    }
//
//    implicit val decodeResultValueType: Decoder[ResultValueType] = Decoder.decodeString.emap {
//      case "String" => Right(ResultValueType.String)
//      case "Long" => Right(ResultValueType.Long)
//      case "BigDecimal" => Right(ResultValueType.BigDecimal)
//      case "Double" => Right(ResultValueType.Double)
//      case other => Left(s"Cannot decode $other as ResultValueType")
//    }
//  }
//
//  // Implicit encoders and decoders for various DTOs
//  implicit val decodeTypedValue: Decoder[MeasureResultDTO.TypedValue] = deriveDecoder
//  implicit val encodeTypedValue: Encoder[MeasureResultDTO.TypedValue] = deriveEncoder
//
//  implicit val decodeMeasureResultDTO: Decoder[MeasureResultDTO] = deriveDecoder
//  implicit val encodeMeasureResultDTO: Encoder[MeasureResultDTO] = deriveEncoder
//
//  implicit val decodeMeasureDTO: Decoder[MeasureDTO] = deriveDecoder
//  implicit val encodeMeasureDTO: Encoder[MeasureDTO] = deriveEncoder
//
//  implicit val decodeMeasurementDTO: Decoder[MeasurementDTO] = deriveDecoder
//  implicit val encodeMeasurementDTO: Encoder[MeasurementDTO] = deriveEncoder
//
//  implicit val decodePartitionDTO: Decoder[PartitionDTO] = deriveDecoder
//  implicit val encodePartitionDTO: Encoder[PartitionDTO] = deriveEncoder
//
//  implicit val decodeCheckpointDTO: Decoder[CheckpointDTO] = deriveDecoder
//  implicit val encodeCheckpointDTO: Encoder[CheckpointDTO] = deriveEncoder
//
//  implicit val decodePartitioningSubmitDTO: Decoder[PartitioningSubmitDTO] = deriveDecoder
//  implicit val encodePartitioningSubmitDTO: Encoder[PartitioningSubmitDTO] = deriveEncoder
//
//  implicit val decodeStringMap: Decoder[Map[String, Option[String]]] = Decoder.decodeMap[String, Option[String]]
//  implicit val encodeStringMap: Encoder[Map[String, Option[String]]] = Encoder.encodeMap[String, Option[String]]
//
//  implicit val decodeAdditionalDataSubmitDTO: Decoder[AdditionalDataSubmitDTO] = deriveDecoder
//  implicit val encodeAdditionalDataSubmitDTO: Encoder[AdditionalDataSubmitDTO] = deriveEncoder
//
//  implicit val decodeAtumContextDTO: Decoder[AtumContextDTO] = deriveDecoder
//  implicit val encodeAtumContextDTO: Encoder[AtumContextDTO] = deriveEncoder
//
//  // JSON serialization and deserialization utilities
//  def asJson[T: Encoder](obj: T): String = {
//    obj.asJson.noSpaces
//  }
//
//  def asJsonPretty[T: Encoder](obj: T): String = {
//    obj.asJson.spaces2
//  }
//
//  def fromJson[T: Decoder](jsonStr: String): T = {
//    io.circe.parser.decode[T](jsonStr) match {
//      case Right(value) => value
//      case Left(error) => throw new RuntimeException(s"Failed to decode JSON: $error")
//    }
//  }
//}
//

import io.circe.{Encoder, Decoder}
import io.circe.syntax._
import io.circe.parser.decode

object JsonUtils {
  def asJson[T: Encoder](obj: T): String = {
    obj.asJson.noSpaces
  }

  def asJsonPretty[T: Encoder](obj: T): String = {
    obj.asJson.spaces2
  }

  def fromJson[T: Decoder](jsonStr: String): T = {
    decode[T](jsonStr) match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(s"Failed to decode JSON: $error")
    }
  }
}

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

package za.co.absa.atum.server.api.database

import cats.Show
import cats.data.NonEmptyList
import doobie.{Get, Put}
import doobie.postgres.implicits._
import io.circe.Decoder
import org.postgresql.jdbc.PgArray
import org.postgresql.util.PGobject
import za.co.absa.atum.model.dto.MeasureResultDTO

import scala.util.{Failure, Success, Try}

object DoobieImplicits {

  private implicit val showPGobject: Show[PGobject] = Show.show(_.getValue.take(250))
  private implicit val showPgArray: Show[PgArray] = Show.fromToString

  implicit val getMapWithOptionStringValues: Get[Map[String, Option[String]]] = Get[Map[String, String]]
      .tmap(map => map.map { case (k, v) => k -> Option(v) })

  object Sequence {

    implicit val get: Get[Seq[String]] = Get[List[String]].map(_.toSeq)
    implicit val put: Put[Seq[String]] = Put[List[String]].contramap(_.toList)

  }

  object Json {

    implicit val jsonArrayPutUsingString: Put[List[String]] = {
      Put.Advanced
        .other[PGobject](
          NonEmptyList.of("json[]")
        )
        .tcontramap { a =>
          val o = new PGobject
          o.setType("json[]")
          o.setValue(a.mkString("{", ",", "}"))
          o
        }
    }

    implicit val jsonArrayGetUsingString: Get[List[String]] = {
      def parsePgArray(a: PgArray): Either[String, List[String]] = {
        Try(a.getArray.asInstanceOf[List[String]]) match {
          case Failure(exception) => Left(exception.toString)
          case Success(value) => Right(value)
        }
      }

      Get.Advanced
        .other[PgArray](
          NonEmptyList.of("json[]")
        )
        .temap(a => parsePgArray(a))
    }

    implicit val jsonPutUsingString: Put[String] = {
      Put.Advanced
        .other[PGobject](
          NonEmptyList.of("json")
        )
        .tcontramap { a =>
          val o = new PGobject
          o.setType("json")
          o.setValue(a)
          o
        }
    }

    implicit val jsonGetUsingString: Get[String] = {
      Get.Advanced
        .other[PGobject](
          NonEmptyList.of("json")
        )
        .temap(a =>
          Try(a.getValue) match {
            case Failure(exception) => Left(exception.toString)
            case Success(value) => Right(value)
          }
        )
    }

  }

  object Jsonb {

    implicit val jsonbArrayPutUsingString: Put[List[String]] = {
      Put.Advanced
        .other[PGobject](
          NonEmptyList.of("jsonb[]")
        )
        .tcontramap { a =>
          val o = new PGobject
          o.setType("jsonb[]")
          o.setValue(a.mkString("{", ",", "}"))
          o
        }
    }

    implicit val jsonbArrayGetUsingString: Get[List[String]] = {
      def parsePgArray(a: PgArray): Either[String, List[String]] = {
        Try(a.getArray.asInstanceOf[List[String]]) match {
          case Failure(exception) => Left(exception.toString)
          case Success(value) => Right(value)
        }
      }

      Get.Advanced
        .other[PgArray](
          NonEmptyList.of("jsonb[]")
        )
        .temap(a => parsePgArray(a))
    }

    implicit val jsonbPutUsingString: Put[String] = {
      Put.Advanced
        .other[PGobject](
          NonEmptyList.of("jsonb")
        )
        .tcontramap { a =>
          val o = new PGobject
          o.setType("jsonb")
          o.setValue(a)
          o
        }
    }

    implicit val jsonbGetUsingString: Get[String] = {
      Get.Advanced
        .other[PGobject](
          NonEmptyList.of("jsonb")
        )
        .temap(a =>
          Try(a.getValue) match {
            case Failure(exception) => Left(exception.toString)
            case Success(value) => Right(value)
          }
        )
    }

    implicit val decodeResultValueType: Decoder[MeasureResultDTO.ResultValueType] = Decoder.decodeString.emap {
      case "String" => Right(MeasureResultDTO.ResultValueType.String)
      case "Long" => Right(MeasureResultDTO.ResultValueType.Long)
      case "BigDecimal" => Right(MeasureResultDTO.ResultValueType.BigDecimal)
      case "Double" => Right(MeasureResultDTO.ResultValueType.Double)
      case other => Left(s"Cannot decode $other as ResultValueType")
    }

    implicit val decodeTypedValue: Decoder[MeasureResultDTO.TypedValue] = Decoder.forProduct2("value", "valueType")(MeasureResultDTO.TypedValue.apply)
    implicit val decodeMeasureResultDTO: Decoder[MeasureResultDTO] = Decoder.forProduct2("mainValue", "supportValues")(MeasureResultDTO.apply)

  }

}

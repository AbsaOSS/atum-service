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
import io.circe.Json
import org.postgresql.jdbc.PgArray
import org.postgresql.util.PGobject

import scala.util.{Failure, Success, Try}

object DoobieImplicits {

  private implicit val showPGobject: Show[PGobject] = Show.show(_.getValue.take(250))
  private implicit val showPgArray: Show[PgArray] = Show.fromToString

  implicit val getMapWithOptionStringValues: Get[Map[String, Option[String]]] = Get[Map[String, String]]
      .tmap(map => map.map { case (k, v) => k -> Option(v) })

  private def circeJsonListToPGJsonArrayString(jsonList: List[Json]): String = {
    val arrayElements = jsonList.map { x =>
      // Convert to compact JSON string and escape inner quotes
      val escapedJsonString = x.noSpaces.replace("\"", "\\\"")
      // Wrap in double quotes for the array element
      s"\"$escapedJsonString\""
    }

    arrayElements.mkString("{", ",", "}")
  }

  object Sequence {

    implicit val get: Get[Seq[String]] = Get[List[String]].map(_.toSeq)
    implicit val put: Put[Seq[String]] = Put[List[String]].contramap(_.toList)

  }

  object Json {

    implicit val jsonArrayPut: Put[List[Json]] = {
      Put.Advanced
        .other[PGobject](
          NonEmptyList.of("json[]")
        )
        .tcontramap { a =>
          val o = new PGobject
          o.setType("json[]")
//          val arrayElements = a.map { x =>
//            // Convert to compact JSON string and escape inner quotes
//            val escapedJsonString = x.noSpaces.replace("\"", "\\\"")
//            // Wrap in double quotes for the array element
//            s"\"$escapedJsonString\""
//          }
          // Join all elements into a single string wrapped in curly braces
          o.setValue(circeJsonListToPGJsonArrayString(a))
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

    implicit val jsonbArrayPut: Put[List[Json]] = {
      Put.Advanced
        .other[PGobject](
          NonEmptyList.of("jsonb[]")
        )
        .tcontramap { a =>
          val o = new PGobject
          o.setType("jsonb[]")
          val arrayElements = a.map { x =>
            // Convert to compact JSON string and escape inner quotes
            val escapedJsonString = x.noSpaces.replace("\"", "\\\"")
            // Wrap in double quotes for the array element
            s"\"$escapedJsonString\""
          }
          // Join all elements into a single string wrapped in curly braces
          o.setValue(arrayElements.mkString("{", ",", "}"))
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
  }

}

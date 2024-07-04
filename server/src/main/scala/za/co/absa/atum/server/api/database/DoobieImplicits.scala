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
import io.circe.{Json => CirceJson}
import org.postgresql.jdbc.PgArray
import org.postgresql.util.PGobject

import scala.util.{Failure, Success, Try}

object DoobieImplicits {

  private implicit val showPgArray: Show[PgArray] = Show.fromToString

  implicit val getMapWithOptionStringValues: Get[Map[String, Option[String]]] = Get[Map[String, String]]
      .tmap(map => map.map { case (k, v) => k -> Option(v) })

  private def circeJsonListToPGJsonArrayString(jsonList: List[CirceJson]): String = {
    val arrayElements = jsonList.map { x =>
      // Convert to compact JSON string and escape inner quotes
      val escapedJsonString = x.noSpaces.replace("\"", "\\\"")
      // Wrap in double quotes for the array element
      s"\"$escapedJsonString\""
    }

    arrayElements.mkString("{", ",", "}")
  }

  private def pgArrayToListOfCirceJson(pgArray: PgArray): Either[String, List[CirceJson]] = {
    Try(pgArray.getArray.asInstanceOf[List[String]].map(CirceJson.fromString)) match {
      case Failure(exception) => Left(exception.toString)
      case Success(value) => Right(value)
    }
  }

  object Sequence {

    implicit val get: Get[Seq[String]] = Get[List[String]].map(_.toSeq)
    implicit val put: Put[Seq[String]] = Put[List[String]].contramap(_.toList)

  }

  object Json {

    implicit val jsonArrayPut: Put[List[CirceJson]] = {
      Put.Advanced
        .other[PGobject](
          NonEmptyList.of("json[]")
        )
        .tcontramap { a =>
          val o = new PGobject
          o.setType("json[]")
          o.setValue(circeJsonListToPGJsonArrayString(a))
          o
        }
    }

    implicit val jsonArrayGet: Get[List[CirceJson]] = {
      Get.Advanced
        .other[PgArray](
          NonEmptyList.of("json[]")
        )
        .temap(pgArray => pgArrayToListOfCirceJson(pgArray))
    }

  }

  object Jsonb {

    implicit val jsonbArrayPut: Put[List[CirceJson]] = {
      Put.Advanced
        .other[PGobject](
          NonEmptyList.of("jsonb[]")
        )
        .tcontramap { a =>
          val o = new PGobject
          o.setType("jsonb[]")
          o.setValue(circeJsonListToPGJsonArrayString(a))
          o
        }
    }

    implicit val jsonbArrayGet: Get[List[CirceJson]] = {
      Get.Advanced
        .other[PgArray](
          NonEmptyList.of("jsonb[]")
        )
        .temap(pgArray => pgArrayToListOfCirceJson(pgArray) )
    }
  }

}

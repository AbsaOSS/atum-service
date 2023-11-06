/*
 * Copyright 2023 ABSA Group Limited
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

package za.co.absa.atum.database.balta.classes

import org.postgresql.util.PGobject

import java.sql.ResultSet
import java.time.{Instant, OffsetDateTime}
import java.util.UUID

class QueryResultRow private[classes](val resultSet: ResultSet) extends AnyVal {
  // this is not stable as resultSet mutates, but good enough for now
  private def safe[T](fnc: => T): Option[T] = {
    val result = fnc
    if (resultSet.wasNull()) {
      None
    } else {
      Some(result)
    }
  }

  def getBoolean(columnLabel: String): Option[Boolean] = safe(resultSet.getBoolean(columnLabel))

  def getString(columnLabel: String): Option[String] = Option(resultSet.getString(columnLabel))

  def getInt(columnLabel: String): Option[Int] = safe(resultSet.getInt(columnLabel))

  def getLong(columnLabel: String): Option[Long] = safe(resultSet.getLong(columnLabel))

  def getUUID(columnLabel: String): Option[UUID] = Option(resultSet.getObject(columnLabel).asInstanceOf[UUID])

  def getOffsetDateTime(columnLabel: String): Option[OffsetDateTime] = Option(resultSet.getObject(columnLabel, classOf[OffsetDateTime]))

  def getInstant(columnLabel: String): Option[Instant] = getOffsetDateTime(columnLabel).map(_.toInstant)

  def getJsonB(columnLabel: String): Option[JsonBString] = {
    Option(resultSet.getObject(columnLabel).asInstanceOf[PGobject])map(pgo => JsonBString(pgo.toString))
  }

  def getAs[T](columnLabel: String): Option[T] = {
    val result = resultSet.getObject(columnLabel).asInstanceOf[T]
    if (resultSet.wasNull()) {
      None
    } else {
      Option(result)
    }
  }

}

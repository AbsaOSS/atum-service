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

package za.co.absa.atum.database.balta.classes.setter

import za.co.absa.atum.database.balta.classes.JsonBString

import java.sql.{Date, PreparedStatement, Time, Timestamp, Types => SqlTypes}
import java.util.UUID
import org.postgresql.util.PGobject

import java.time.{Instant, LocalDate, LocalTime, OffsetDateTime, ZoneId, ZoneOffset}
abstract class SetterFnc extends ((PreparedStatement, Int) => Unit) {
  def sqlEntry: String = "?"
}

object SetterFnc {
  def createSetterFnc[T: AllowedParamTypes](value: T): SetterFnc = {
    value match {
      case b: Boolean                  => (prep: PreparedStatement, position: Int) => {prep.setBoolean(position, b)}
      case i: Int                      => (prep: PreparedStatement, position: Int) => {prep.setInt(position, i)}
      case l: Long                     => (prep: PreparedStatement, position: Int) => {prep.setLong(position, l)}
      case d: Double                   => (prep: PreparedStatement, position: Int) => {prep.setDouble(position, d)}
      case f: Float                    => (prep: PreparedStatement, position: Int) => {prep.setFloat(position, f)}
      case bd: BigDecimal              => (prep: PreparedStatement, position: Int) => {prep.setBigDecimal(position, bd.bigDecimal)}
      case ch: Char                    => (prep: PreparedStatement, position: Int) => {prep.setString(position, ch.toString)}
      case s: String                   => (prep: PreparedStatement, position: Int) => {prep.setString(position, s)}
      case u: UUID                     => new UuidSetterFnc(u)
      case js: JsonBString             => new JsonBSetterFnc(js)
      case i: Instant                  => (prep: PreparedStatement, position: Int) => {prep.setObject(position, OffsetDateTime.ofInstant(i, ZoneOffset.UTC))}
      case ts: OffsetDateTime          => (prep: PreparedStatement, position: Int) => {prep.setObject(position, ts)}
      case lt: LocalTime               => (prep: PreparedStatement, position: Int) => {prep.setTime(position, Time.valueOf(lt))}
      case ld: LocalDate               => (prep: PreparedStatement, position: Int) => {prep.setDate(position, Date.valueOf(ld))}
      case CustomDBType(value, dbType) => new CustomDBTypeSetterFnc(value, dbType)
    }
  }

  val nullSetterFnc: SetterFnc = (prep: PreparedStatement, position: Int) => {
    prep.setNull(position, SqlTypes.NULL)
  }

  private class UuidSetterFnc(value: UUID) extends SetterFnc {
    def apply(prep: PreparedStatement, position: Int): Unit = {
      prep.setObject(position, value)
    }
  }

  private class JsonBSetterFnc(value: JsonBString) extends SetterFnc {
    private val jsonObject = new PGobject()
    jsonObject.setType("jsonb")
    jsonObject.setValue(value.value)

    def apply(prep: PreparedStatement, position: Int): Unit = {
      prep.setObject(position, jsonObject)
    }
  }

  private class CustomDBTypeSetterFnc(value: String, dbType: String) extends SetterFnc {
    def apply(prep: PreparedStatement, position: Int): Unit = {
      prep.setString(position, value)
    }

    override def sqlEntry: String = s"?::$dbType"
  }
}

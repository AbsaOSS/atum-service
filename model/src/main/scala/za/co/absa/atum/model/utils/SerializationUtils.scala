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

import org.json4s.JsonAST.JString
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{write, writePretty}
import org.json4s.{CustomSerializer, Formats, JNull, NoTypeHints, ext}
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType._

import java.time.format.DateTimeFormatter

object SerializationUtils {

  implicit private val formatsJson: Formats =
    Serialization.formats(NoTypeHints).withBigDecimal +
      ext.UUIDSerializer +
      ZonedDateTimeSerializer +
      ResultValueTypeSerializer

  // TODO "yyyy-MM-dd'T'hh:mm:ss.SSS'Z'" OR TODO "yyyy-MM-dd HH:mm:ss.SSSSSSX"
  val timestampFormat: DateTimeFormatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

  /**
   * The method returns arbitrary object as a Json string.
   *
   * @return A string representing the object in Json format
   */
  def asJson[T <: AnyRef](obj: T): String = {
    write[T](obj)
  }

  /**
   * The method returns arbitrary object as a pretty Json string.
   *
   * @return A string representing the object in Json format
   */
  def asJsonPretty[T <: AnyRef](obj: T): String = {
    writePretty[T](obj)
  }

  /**
   * The method returns arbitrary object parsed from Json string.
   *
   * @return An object deserialized from the Json string
   */
  def fromJson[T <: AnyRef](jsonStr: String)(implicit m: Manifest[T]): T = {
    Serialization.read[T](jsonStr)
  }

  private case object ResultValueTypeSerializer extends CustomSerializer[ResultValueType](format => (
    {
      case JString(resultValType) => resultValType match {
        case "String"       => String
        case "Long"         => Long
        case "BigDecimal"   => BigDecimal
        case "Double"       => Double
      }
      case JNull => null
    },
    {
      case resultValType: ResultValueType => resultValType match {
        case String       => JString("String")
        case Long         => JString("Long")
        case BigDecimal   => JString("BigDecimal")
        case Double       => JString("Double")
      }
    }))

}

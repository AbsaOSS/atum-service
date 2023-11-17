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
import org.json4s.{CustomSerializer, JNull}

import java.time.ZonedDateTime

case object ZonedDateTimeSerializer extends CustomSerializer[ZonedDateTime](_ => (
  {
    case JString(s) =>
      println(s)
      ZonedDateTime.parse(s, SerializationUtils.timestampFormat)
    case JNull => null
  },
  {
    case d: ZonedDateTime => JString(SerializationUtils.timestampFormat.format(d))
  }
))

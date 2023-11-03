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

import java.time.{Instant, ZonedDateTime}
import java.util.UUID

sealed trait AllowedParamTypes[T]

object AllowedParamTypes {
  // where new type is added remember to add an appropriate `SetterFnc`
  implicit object BooleanParamType extends AllowedParamTypes[Boolean]

  implicit object IntParamType extends AllowedParamTypes[Int]

  implicit object LongParamType extends AllowedParamTypes[Long]

  implicit object StringParamType extends AllowedParamTypes[String]

  implicit object UUIDParamType extends AllowedParamTypes[UUID]

  implicit object JsonBParamType extends AllowedParamTypes[JsonBString]

  implicit object InstantParamType extends AllowedParamTypes[Instant]

  implicit object ZonedDateTimeParamType extends AllowedParamTypes[ZonedDateTime]


}

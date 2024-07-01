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

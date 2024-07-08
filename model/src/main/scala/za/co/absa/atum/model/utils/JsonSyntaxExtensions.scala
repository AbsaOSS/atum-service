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

import io.circe.parser.decode
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

object JsonSyntaxExtensions {

    implicit class JsonSerializationSyntax[T: Encoder](obj: T) {
      def asJsonString: String = obj.asJson.noSpaces

      def asJsonStringPretty: String = obj.asJson.spaces2
    }

    implicit class JsonDeserializationSyntax(jsonStr: String) {
      def as[T: Decoder]: T = {
        decode[T](jsonStr) match {
          case Right(value) => value
          case Left(error) => throw new RuntimeException(s"Failed to decode JSON: $error")
        }
      }
    }

}

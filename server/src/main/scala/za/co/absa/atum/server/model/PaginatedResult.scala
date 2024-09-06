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

package za.co.absa.atum.server.model

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

sealed trait PaginatedResult[R] {
  def data: Seq[R]
}

object PaginatedResult {

  case class ResultHasMore[R](data: Seq[R]) extends PaginatedResult[R]
  object ResultHasMore {
    implicit def encoder[T: Encoder]: Encoder[ResultHasMore[T]] = deriveEncoder
    implicit def decoder[T: Decoder]: Decoder[ResultHasMore[T]] = deriveDecoder
  }

  case class ResultNoMore[R](data: Seq[R]) extends PaginatedResult[R]
  object ResultNoMore {
    implicit def encoder[T: Encoder]: Encoder[ResultNoMore[T]] = deriveEncoder
    implicit def decoder[T: Decoder]: Decoder[ResultNoMore[T]] = deriveDecoder
  }

}

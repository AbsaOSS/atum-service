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

package za.co.absa.atum.server.implicits

import io.circe.DecodingFailure

object SeqImplicits {
  implicit class SeqEnhancements[T](val seq: Seq[T]) extends AnyVal {

    def seqDecode[R](decodingFnc: T => Either[DecodingFailure, R]): Either[DecodingFailure, Seq[R]] = {
      seq.foldLeft(Right(List.empty[R]): Either[DecodingFailure, List[R]]) { (acc, item) =>
        for {
          decodedList <- acc
          decodedItem <- decodingFnc(item)
        } yield decodedItem :: decodedList
      }.map(_.reverse)
    }
  }
}

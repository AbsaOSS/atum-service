/*
 * Copyright 2025 ABSA Group Limited
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

package za.co.absa.atum.testing.data

import sttp.client3.Identity
import sttp.client3.monad.IdMonad
import sttp.monad.MonadError
import za.co.absa.atum.reader.core.RequestResult.{RequestOK, RequestResult}
import za.co.absa.atum.reader.result.Page

object PageData {
  private implicit val monad: MonadError[Identity] = IdMonad

  case class TestItem(group: Int, value: String)

  val items1: Vector[TestItem] = Vector(
    TestItem(1, "a"),
    TestItem(1, "b"),
    TestItem(2, "c"),
    TestItem(2, "d"),
    TestItem(3, "e"),
    TestItem(3, "f")
  )

  val items2: Vector[TestItem] = Vector(
    TestItem(3, "aa"),
    TestItem(4, "bb"),
    TestItem(5, "dd"),
    TestItem(4, "cc")
  )

  def roller(limit: Int, offset: Long): RequestResult[Page[TestItem, Identity]] = {
    offset match {
      case 0 =>
        RequestOK(
          Page[TestItem, Identity](
            items = items1,
            hasNext = true,
            limit = limit,
            pageStart = 0,
            pageEnd = 5,
            roller
          )
        )
      case 6 =>     RequestOK(
        Page[TestItem, Identity](
          items = items2,
          hasNext = false,
          limit = limit,
          pageStart = 6,
          pageEnd = 9,
          roller
        )
      )

    }
  }
}

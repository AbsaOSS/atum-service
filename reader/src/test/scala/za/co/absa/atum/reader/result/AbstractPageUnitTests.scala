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

package za.co.absa.atum.reader.result

import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.client3.Identity
import sttp.client3.monad.IdMonad
import sttp.monad.MonadError


class AbstractPageUnitTests extends AnyFunSuiteLike {
  private implicit val monad: MonadError[Identity] = IdMonad

  test("Basic test") {
    val page = new AbstractPage[Iterable[Int], Identity] {
      override def items: Iterable[Int] = Seq(1, 2, 3)
      override def hasNext: Boolean = true
      override def limit: Int = 3
      override def pageStart: Long = 0
      override def pageEnd: Long = 2
    }

    assert(page.items.size == 3)
    assert(page.hasNext)
    assert(page.limit == 3)
    assert(page.pageStart == 0)
    assert(page.pageSize == 3)
    assert(!page.hasPrior)

    val anotherPage = new AbstractPage[Iterable[Int], Identity] {
      override def items: Iterable[Int] = Seq(1, 2, 3)
      override def hasNext: Boolean = true
      override def limit: Int = 3
      override def pageStart: Long = 1
      override def pageEnd: Long = 2
    }
    assert(anotherPage.hasPrior)
  }
}

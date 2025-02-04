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

package za.co.absa.atum.reader.result

import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.reader.core.RequestResult.{RequestFail, RequestOK, RequestResult}
import za.co.absa.atum.reader.exceptions.RequestException.NoDataException
import za.co.absa.atum.testing.data.PageData
import za.co.absa.atum.testing.data.PageData.TestItem
import za.co.absa.atum.testing.implicits.AbstractPageImplicits.PageEnhancements

class PageUnitTests extends AnyFunSuiteLike {

  test("Get the items of a page") {
    val Right(page) = PageData.roller(6, 0)

    assert(page(0) == TestItem(1, "a"))
    assert(page(5) == TestItem(3, "f"))
  }

  test("Mapping the page items") {
    val Right(origPage) = PageData.roller(6, 0)

    val mapped = origPage.map(_.value.toUpperCase)

    assert(mapped.hasNext == origPage.hasNext)
    assert(mapped.limit == origPage.limit)
    assert(mapped.pageStart == origPage.pageStart)
    assert(mapped.pageEnd == origPage.pageEnd)
    assert(mapped.items == Vector("A", "B", "C", "D", "E", "F"))

    val Right(nextPage) = mapped.next
    assert(nextPage.items == Vector("AA", "BB", "DD", "CC"))
  }

  test("Rolling of the pages") {
    val Right(page1) = PageData.roller(6, 0)
    val Right(page2) = page1.next

    page2.assertPage(PageData.items2, hasNext = false, limit = 6, pageStart = 6, pageEnd = 9)
    val noPage1 = page2.next
    assert(noPage1 == RequestFail(NoDataException("No next page")))

    val Right(page3) = page1.next(100)
    page3.assertPage(PageData.items2, hasNext = false, limit = 100, pageStart = 6, pageEnd = 9)

    val Right(page4) = page2.prior(42)
    page4.assertPage(PageData.items1, hasNext = true, limit = 42, pageStart = 0, pageEnd = 5)
    assert(!page4.hasPrior)

    val noPage2 = page4.prior()
    assert(noPage2 == RequestFail(NoDataException("No prior page")))
  }

  test("Concatenation of pages") {
    val Right(page1) = PageData.roller(6, 0)
    val Right(page2) = page1.next

    val page3 = page1 + page2
    page3.assertPage(PageData.items1 ++ PageData.items2, hasNext = false, limit = 6, pageStart = 0, pageEnd = 9)
  }

  test("Grouping of the page items") {
    val Right(page1) = PageData.roller(6, 0)
    val grouped = page1.groupBy(_.group)

    assert(grouped.groupCount == 3)
    assert(grouped.keys.toList == List(1, 2, 3))

    assert(grouped(1) == Vector(TestItem(1, "a"), TestItem(1, "b")))
    assert(grouped(2) == Vector(TestItem(2, "c"), TestItem(2, "d")))
    assert(grouped(3) == Vector(TestItem(3, "e"), TestItem(3, "f")))
  }
}


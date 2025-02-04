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
import za.co.absa.atum.reader.core.RequestResult.RequestFail
import za.co.absa.atum.reader.exceptions.RequestException.NoDataException
import za.co.absa.atum.testing.data.PageData
import za.co.absa.atum.testing.implicits.AbstractPageImplicits.GroupedPageEnhancements

import scala.collection.immutable.ListMap


class GroupedPageUnitTests extends AnyFunSuiteLike {
  private val initialPage = {
    val Right(ungrouped) = PageData.roller(6, 0)
    ungrouped.groupBy(_.group)
  }

  test("Getting items, group keys, and group count") {
    assert(initialPage(3) == Vector(PageData.TestItem(3, "e"), PageData.TestItem(3, "f")))
    assert(initialPage.groupCount == 3)
    assert(initialPage.keys.toList == List(1, 2, 3))
  }

  test("Mapping of the page") {
    val mappedPage1 = initialPage.map(x => (x._1.toString, x._2.map(_.value.toUpperCase)))

    mappedPage1.assertGroupedPage(
      ListMap(
        "1" -> Vector("A", "B"),
        "2" -> Vector("C", "D"),
        "3" -> Vector("E", "F")
      ),
      hasNext = true,
      limit = 6,
      pageStart = 0,
      pageEnd = 5
    )

    val Right(mappedPage2) = mappedPage1.next
    mappedPage2.assertGroupedPage(
      ListMap(
        "3" -> Vector("AA"),
        "4" -> Vector("BB", "CC"),
        "5" -> Vector("DD")
      ),
      hasNext = false,
      limit = 6,
      pageStart = 6,
      pageEnd = 9
    )
  }

  test("Mapping of the page values") {
    val mappedPage1 = initialPage.mapValues(_.value.toUpperCase)

    mappedPage1.assertGroupedPage(
      ListMap(
        1 -> Vector("A", "B"),
        2 -> Vector("C", "D"),
        3 -> Vector("E", "F")
      ),
      hasNext = true,
      limit = 6,
      pageStart = 0,
      pageEnd = 5
    )

    val Right(mappedPage2) = mappedPage1.next
    mappedPage2.assertGroupedPage(
      ListMap(
        3 -> Vector("AA"),
        4 -> Vector("BB", "CC"),
        5 -> Vector("DD")
      ),
      hasNext = false,
      limit = 6,
      pageStart = 6,
      pageEnd = 9
    )
  }

  test("Rolling of the pages") {
    val Right(nextPage1) = initialPage.next
    nextPage1.assertGroupedPage(
      ListMap(
        3 -> Vector(PageData.TestItem(3, "aa")),
        4 -> Vector(PageData.TestItem(4, "bb"), PageData.TestItem(4, "cc")),
        5 -> Vector(PageData.TestItem(5, "dd"))
      ),
      hasNext = false,
      limit = 6,
      pageStart = 6,
      pageEnd = 9
    )
    assert(nextPage1.hasPrior)

    val noPage1 = nextPage1.next
    assert(noPage1 == RequestFail(NoDataException("No next page")))

    val Right(nextPage2) = initialPage.next(17)
    nextPage2.assertGroupedPage(
      ListMap(
        3 -> Vector(PageData.TestItem(3, "aa")),
        4 -> Vector(PageData.TestItem(4, "bb"), PageData.TestItem(4, "cc")),
        5 -> Vector(PageData.TestItem(5, "dd"))
      ),
      hasNext = false,
      limit = 17,
      pageStart = 6,
      pageEnd = 9
    )

    val Right(priorPage1) = nextPage2.prior
    priorPage1.assertGroupedPage(
      ListMap(
        1 -> Vector(PageData.TestItem(1, "a"), PageData.TestItem(1, "b")),
        2 -> Vector(PageData.TestItem(2, "c"), PageData.TestItem(2, "d")),
        3 -> Vector(PageData.TestItem(3, "e"), PageData.TestItem(3, "f"))
      ),
      hasNext = true,
      limit = 17,
      pageStart = 0,
      pageEnd = 5
    )

    val Right(priorPage2) = nextPage2.prior(7)
    priorPage2.assertGroupedPage(
      ListMap(
        1 -> Vector(PageData.TestItem(1, "a"), PageData.TestItem(1, "b")),
        2 -> Vector(PageData.TestItem(2, "c"), PageData.TestItem(2, "d")),
        3 -> Vector(PageData.TestItem(3, "e"), PageData.TestItem(3, "f"))
      ),
      hasNext = true,
      limit = 7,
      pageStart = 0,
      pageEnd = 5
    )

    assert(!priorPage2.hasPrior)

    val noPage2 = priorPage2.prior
    assert(noPage2 == RequestFail(NoDataException("No prior page")))
  }

  test("Concatenation of pages") {
    val Right(nextPage1) = initialPage.next
    val concatenatedPage = initialPage + nextPage1
    concatenatedPage.assertGroupedPage(
      ListMap(
        1 -> Vector(PageData.TestItem(1, "a"), PageData.TestItem(1, "b")),
        2 -> Vector(PageData.TestItem(2, "c"), PageData.TestItem(2, "d")),
        3 -> Vector(PageData.TestItem(3, "e"), PageData.TestItem(3, "f"), PageData.TestItem(3, "aa")),
        4 -> Vector(PageData.TestItem(4, "bb"), PageData.TestItem(4, "cc")),
        5 -> Vector(PageData.TestItem(5, "dd"))
      ),
      hasNext = false,
      limit = 6,
      pageStart = 0,
      pageEnd = 9
    )
  }

}

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

package za.co.absa.atum.testing.implicits

import za.co.absa.atum.reader.result.{AbstractPage, GroupedPage, Page}

import scala.collection.immutable.ListMap

object AbstractPageImplicits {
  implicit class PageEnhancements[T, F[_]](val page: Page[T, F]) extends AnyVal {
    def assertPage(items: Vector[T],
                   hasNext: Boolean,
                   limit: Int,
                   pageStart: Long,
                   pageEnd: Long): Unit = {
      assert(items == page.items, s"Expected items: $items, but got: ${page.items}")
      assert(hasNext == page.hasNext, s"Expected hasNext: $hasNext, but got: ${page.hasNext}")
      assert(limit == page.limit, s"Expected limit: $limit, but got: ${page.limit}")
      assert(pageStart == page.pageStart, s"Expected pageStart: $pageStart, but got: ${page.pageStart}")
      assert(pageEnd == page.pageEnd, s"Expected pageEnd: $pageEnd, but got: ${page.pageEnd}")
    }
  }

  implicit class GroupedPageEnhancements[K, V, F[_]](val page: GroupedPage[K, V, F]) extends AnyVal {
    def assertGroupedPage(items: ListMap[K, Vector[V]],
                          hasNext: Boolean,
                          limit: Int,
                          pageStart: Long,
                          pageEnd: Long): Unit = {
      assert(items == page.items, s"Expected items: $items, but got: ${page.items}")
      assert(hasNext == page.hasNext, s"Expected hasNext: $hasNext, but got: ${page.hasNext}")
      assert(limit == page.limit, s"Expected limit: $limit, but got: ${page.limit}")
      assert(pageStart == page.pageStart, s"Expected pageStart: $pageStart, but got: ${page.pageStart}")
      assert(pageEnd == page.pageEnd, s"Expected pageEnd: $pageEnd, but got: ${page.pageEnd}")
    }
  }
}

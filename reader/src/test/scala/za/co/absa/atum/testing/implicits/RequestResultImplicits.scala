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

import za.co.absa.atum.reader.core.RequestResult.RequestResult
import za.co.absa.atum.reader.result.Page
import za.co.absa.atum.testing.implicits.AbstractPageImplicits.PageEnhancements

object RequestResultImplicits {
  implicit class RequestResultPageEnhancements[T, F[_]](val pageResult: RequestResult[Page[T, F]]) extends AnyVal {
    def assertPage(items: Vector[T],
                   hasNext: Boolean,
                   limit: Int,
                   pageStart: Long,
                   pageEnd: Long): Unit = {
      pageResult match {
        case Right(page) => page.assertPage(items, hasNext, limit, pageStart, pageEnd)
        case _ => throw new AssertionError("Expected a page result")
      }
    }
  }

}

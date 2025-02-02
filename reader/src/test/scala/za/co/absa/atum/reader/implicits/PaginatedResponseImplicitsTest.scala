/*
 * Copyright 2024 ABSA Group Limited
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

package za.co.absa.atum.reader.implicits

import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.client3.Identity
import sttp.client3.monad.IdMonad
import sttp.monad.MonadError
import za.co.absa.atum.model.envelopes.Pagination
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.reader.core.RequestResult.{RequestFail, RequestResult}
import za.co.absa.atum.reader.exceptions.RequestException
import za.co.absa.atum.reader.implicits.PaginatedResponseImplicits.PaginatedResponseMonadEnhancements
import za.co.absa.atum.reader.result.Page

class PaginatedResponseImplicitsTest extends AnyFunSuiteLike {
  private implicit val monad: MonadError[Identity] = IdMonad

  test("toPage should convert PaginatedResponse to Page") {
    def roll(pageSize: Int, offset: Long): Identity[RequestResult[Page[Int, Identity]]] = {
      IdMonad.unit(RequestFail(new RequestException("Not used"){}))
    }

    val source = PaginatedResponse(Seq(1, 2, 3),
      pagination = Pagination(offset = 1, limit = 3, hasMore = false)
    )
    val result = source.toPage(roll)
    assert(result.items == Vector(1, 2, 3))
    assert(!result.hasNext)
    assert(result.limit == 3)
    assert(result.pageStart == 1)
  }
}

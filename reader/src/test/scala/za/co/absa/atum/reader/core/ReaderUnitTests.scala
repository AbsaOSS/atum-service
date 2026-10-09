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

package za.co.absa.atum.reader.core

import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.capabilities
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Identity, SttpBackend}
import sttp.client3.monad.IdMonad
import sttp.monad.MonadError
import za.co.absa.atum.model.envelopes.Pagination
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.reader.core.RequestResult.{RequestFail, RequestOK, RequestResult}
import za.co.absa.atum.reader.exceptions.RequestException.ParsingException
import za.co.absa.atum.reader.server.ServerConfig

class ReaderUnitTests extends AnyFunSuiteLike {
  class ReaderForTesting[F[_]: MonadError](
                                            implicit serverConfig: ServerConfig,
                                            backend: SttpBackend[F, Any]
                                          )
    extends Reader[F]{

    override def mapRequestResultF[I, O](requestResult: RequestResult[I], f: I => F[RequestResult[O]]): F[RequestResult[O]] = {
      super.mapRequestResultF(requestResult, f)
    }

    override def queryAllPages[T](
      pageSize: Int,
      queryPage: (Int, Long) => F[RequestResult[PaginatedResponse[T]]]
    ): F[RequestResult[Seq[T]]] = {
      super.queryAllPages(pageSize, queryPage)
    }
  }

  private implicit val serverConfig: ServerConfig = ServerConfig("http://localhost:8080")
  private implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
  private implicit val monad: MonadError[Identity] = IdMonad

  test("RequestResult should be mapped if Right") {
    def fnc(b: Int): Identity[RequestResult[String]] = Right(b.toString)
    val reader = new ReaderForTesting[Identity]
    val requestResult = RequestOK(1)
    val result = reader.mapRequestResultF(requestResult, fnc)
    assert(result == Right("1"))
  }

  test("RequestResult should no map if Left") {
    def fnc(b: Int): Identity[RequestResult[String]] = Right(b.toString)
    val reader = new ReaderForTesting[Identity]
    val requestResult = RequestFail(ParsingException("Just a test", ""))
    val result = reader.mapRequestResultF(requestResult, fnc)
    assert(result == requestResult)
  }

  test("All pages are queried, without exhausting the stack even when the effect is synchronous") {
    val recordCount = 100000L
    def queryPage(limit: Int, offset: Long): Identity[RequestResult[PaginatedResponse[Long]]] =
      RequestOK(PaginatedResponse(Seq(offset), Pagination(limit, offset, hasMore = offset + limit < recordCount)))
    val reader = new ReaderForTesting[Identity]
    val result = reader.queryAllPages(1, queryPage)
    assert(result == RequestOK(0L until recordCount))
  }

  test("Querying all pages stops at the first page failing") {
    def queryPage(limit: Int, offset: Long): Identity[RequestResult[PaginatedResponse[Long]]] =
      if (offset < 2) RequestOK(PaginatedResponse(Seq(offset), Pagination(limit, offset, hasMore = true)))
      else RequestFail(ParsingException("Just a test", ""))
    val reader = new ReaderForTesting[Identity]
    val result = reader.queryAllPages(1, queryPage)
    assert(result == RequestFail(ParsingException("Just a test", "")))
  }
}

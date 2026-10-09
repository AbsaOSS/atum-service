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

import io.circe.Decoder
import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.client3.SttpBackend
import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadError
import za.co.absa.atum.model.dto.PartitionDTO
import za.co.absa.atum.model.envelopes.Pagination
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax
import za.co.absa.atum.reader.core.RequestResult.{RequestFail, RequestOK, RequestResult}
import za.co.absa.atum.reader.exceptions.RequestException.ParsingException
import za.co.absa.atum.reader.server.ServerConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class Reader_FutureUnitTests extends AnyFunSuiteLike {
  private implicit val serverConfig: ServerConfig = ServerConfig("http://localhost:8080")

  private class ReaderForTest[F[_]](implicit serverConfig: ServerConfig, backend: SttpBackend[F, Any], ev: MonadError[F])
    extends Reader {
    override def getQuery[R: Decoder](endpointUri: String, params: Map[String, String]): F[RequestResult[R]] = super.getQuery(endpointUri, params)
    override def queryAllPages[T](
      pageSize: Int,
      queryPage: (Int, Long) => F[RequestResult[PaginatedResponse[T]]]
    ): F[RequestResult[Seq[T]]] = super.queryAllPages(pageSize, queryPage)
  }

  test("Using Future based backend") {
    import za.co.absa.atum.reader.implicits.future.futureMonadError

    val partitionDTO = PartitionDTO("someKey", "someValue")
    implicit val server: SttpBackend[Future, Any] = SttpBackendStub.asynchronousFuture
      .whenAnyRequest.thenRespond(partitionDTO.asJsonString)

    val reader = new ReaderForTest
    val resultToBe = reader.getQuery[PartitionDTO]("/test", Map.empty)
    val result = Await.result(resultToBe, Duration(3, "second"))
    assert(result == Right(partitionDTO))
  }

  test("Querying all pages using Future based backend") {
    import za.co.absa.atum.reader.implicits.future.futureMonadError

    implicit val server: SttpBackend[Future, Any] = SttpBackendStub.asynchronousFuture
    val recordCount = 1000L
    // alternating already completed and asynchronously completing pages
    def queryPage(limit: Int, offset: Long): Future[RequestResult[PaginatedResponse[Long]]] = {
      def page = RequestOK(PaginatedResponse(Seq(offset), Pagination(limit, offset, hasMore = offset + limit < recordCount)))
      if (offset % 2 == 0) Future.successful(page) else Future(page)
    }

    val reader = new ReaderForTest
    val result = Await.result(reader.queryAllPages(1, queryPage), Duration(10, "second"))
    assert(result == RequestOK(0L until recordCount))
  }

  test("Querying all pages using Future based backend stops at the first page failing") {
    import za.co.absa.atum.reader.implicits.future.futureMonadError

    implicit val server: SttpBackend[Future, Any] = SttpBackendStub.asynchronousFuture
    def queryPage(limit: Int, offset: Long): Future[RequestResult[PaginatedResponse[Long]]] = Future {
      if (offset < 2) RequestOK(PaginatedResponse(Seq(offset), Pagination(limit, offset, hasMore = true)))
      else RequestFail(ParsingException("Just a test", ""))
    }

    val reader = new ReaderForTest
    val result = Await.result(reader.queryAllPages(1, queryPage), Duration(3, "second"))
    assert(result == RequestFail(ParsingException("Just a test", "")))
  }

}

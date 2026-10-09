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

import cats.effect.unsafe.implicits.global
import io.circe.Decoder
import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.client3.SttpBackend
import sttp.client3.testing.SttpBackendStub
import sttp.monad.{MonadAsyncError, MonadError}
import za.co.absa.atum.model.dto.PartitionDTO
import za.co.absa.atum.model.envelopes.Pagination
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax
import za.co.absa.atum.reader.core.RequestResult.{RequestOK, RequestResult}
import za.co.absa.atum.reader.server.ServerConfig

class Reader_CatsIOUnitTests extends AnyFunSuiteLike {
  private implicit val serverConfig: ServerConfig = ServerConfig("http://localhost:8080")

  private class ReaderForTest[F[_]](implicit serverConfig: ServerConfig, backend: SttpBackend[F, Any], ev: MonadError[F])
    extends Reader {
    override def getQuery[R: Decoder](endpointUri: String, params: Map[String, String]): F[RequestResult[R]] = super.getQuery(endpointUri, params)
    override def queryAllPages[T](
      pageSize: Int,
      queryPage: (Int, Long) => F[RequestResult[PaginatedResponse[T]]]
    ): F[RequestResult[Seq[T]]] = super.queryAllPages(pageSize, queryPage)
  }

  test("Using Cats IO based backend") {
    import cats.effect.IO
    import za.co.absa.atum.reader.implicits.io.catsIOMonadError

    val partitionDTO = PartitionDTO("someKey", "someValue")
    implicit val server: SttpBackendStub[IO, Any] = SttpBackendStub[IO, Any](implicitly[MonadAsyncError[IO]])
      .whenAnyRequest.thenRespond(partitionDTO.asJsonString)

    val reader = new ReaderForTest
    val query = reader.getQuery[PartitionDTO]("/test", Map.empty)
    val result = query.unsafeRunSync()
    assert(result == Right(partitionDTO))
  }

  test("Querying all pages using Cats IO based backend, the query being repeatable") {
    import cats.effect.IO
    import za.co.absa.atum.reader.implicits.io.catsIOMonadError

    implicit val server: SttpBackendStub[IO, Any] = SttpBackendStub[IO, Any](implicitly[MonadAsyncError[IO]])
    val recordCount = 10000L
    def queryPage(limit: Int, offset: Long): IO[RequestResult[PaginatedResponse[Long]]] =
      IO(RequestOK(PaginatedResponse(Seq(offset), Pagination(limit, offset, hasMore = offset + limit < recordCount))))

    val reader = new ReaderForTest
    val query = reader.queryAllPages(1, queryPage)
    assert(query.unsafeRunSync() == RequestOK(0L until recordCount))
    assert(query.unsafeRunSync() == RequestOK(0L until recordCount))
  }

}

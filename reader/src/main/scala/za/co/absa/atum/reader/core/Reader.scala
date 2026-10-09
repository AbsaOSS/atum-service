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
import sttp.client3.{Identity, RequestT, ResponseException, SttpBackend, basicRequest}
import sttp.client3.circe.asJson
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.monad.syntax._
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.reader.core.Reader.{Detached, NextPage, PageHandOver, Pending}
import za.co.absa.atum.reader.core.RequestResult._
import za.co.absa.atum.reader.server.ServerConfig
import za.co.absa.atum.reader.exceptions.RequestException.CirceError

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 *  Reader is a base class for reading data from a remote server.
 *  @param serverConfig    - the configuration how to reach the Atum server
 *  @param backend         - sttp backend to use to send requests
 *  @tparam F              - the monadic effect used to get the data (e.g. Future, IO, Task, etc.)
 *                        the context bind for the F type is MonadError to allow not just map, flatMap but eventually
 *                        also error handling easily on a higher level
 */
abstract class Reader[F[_]](implicit
  serverConfig: ServerConfig,
  backend: SttpBackend[F, Any],
  me: MonadError[F]
) {

  protected def mapRequestResultF[I, O](
    requestResult: RequestResult[I],
    f: I => F[RequestResult[O]]
  ): F[RequestResult[O]] = requestResult match {
    case Right(b) => f(b)
    case Left(a) => me.unit(Left(a))
  }

  /**
   *  Queries the pages one after another, starting at offset 0, until the server reports there is no more data.
   *
   *  The paging is stack-safe for any effect, including the strict ones (e.g. `Identity`), where `flatMap` runs its
   *  continuation right away and a plain recursion would add stack frames with each page.
   *
   *  @param pageSize  - the size of the page (record count) to query
   *  @param queryPage - function querying a page of the given size (limit) at the given offset
   *  @return          - the records of all the pages, in order, or the first error encountered
   */
  protected def queryAllPages[T](
    pageSize: Int,
    queryPage: (Int, Long) => F[RequestResult[PaginatedResponse[T]]]
  ): F[RequestResult[Seq[T]]] = {
    // If the continuation of a page runs before `flatMap` returns (strict effect), it only hands the records collected
    // so far over to the loop, which then queries the next page iteratively. Otherwise (asynchronous or lazy effect)
    // the continuation queries the next page itself, the effect taking care of the stack. The hand-over is atomic, as
    // the continuation of an asynchronous effect may run on another thread at the same time.
    @tailrec
    def queryFrom(offset: Long, collected: Vector[T]): F[RequestResult[Seq[T]]] = {
      val handOver = new AtomicReference[PageHandOver[T]](Pending)
      val result = queryPage(pageSize, offset).flatMap {
        case Right(page) if page.pagination.hasMore =>
          val nextCollected = collected ++ page.data
          if (handOver.compareAndSet(Pending, new NextPage(nextCollected))) {
            me.unit(RequestOK[Seq[T]](nextCollected)) // discarded, the loop continues with the next page
          } else {
            queryFromWithinEffect(offset + pageSize, nextCollected)
          }
        case Right(page) => me.unit(RequestOK[Seq[T]](collected ++ page.data))
        case Left(error) => me.unit(RequestFail[Seq[T]](error))
      }
      handOver.getAndSet(Detached) match {
        case nextPage: NextPage[T] => queryFrom(offset + pageSize, nextPage.collected)
        case _ => result
      }
    }
    // not a tail call, therefore kept apart for `queryFrom` to remain tail-recursive
    def queryFromWithinEffect(offset: Long, collected: Vector[T]): F[RequestResult[Seq[T]]] =
      queryFrom(offset, collected)

    queryFrom(0, Vector.empty)
  }

  protected def getQuery[R: Decoder](
    endpointUri: String,
    params: Map[String, String] = Map.empty
  ): F[RequestResult[R]] = {
    val endpointToQuery = serverConfig.host + endpointUri
    val uri = Uri.unsafeParse(endpointToQuery).addParams(params)
    val request: RequestT[Identity, Either[ResponseException[String, CirceError], R], Any] = basicRequest
      .get(uri)
      .response(asJson[R])

    val response = backend.send(request)

    response.map(_.toRequestResult)
  }
}

object Reader {
  private sealed class PageHandOver[+T]
  private final class NextPage[+T](val collected: Vector[T]) extends PageHandOver[T]
  private val Pending: PageHandOver[Nothing] = new PageHandOver
  private val Detached: PageHandOver[Nothing] = new PageHandOver
}

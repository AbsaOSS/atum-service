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

package za.co.absa.atum.reader.result

import sttp.monad.MonadError
import sttp.monad.syntax._
import za.co.absa.atum.reader.basic.RequestResult.{RequestFail, RequestResult}
import za.co.absa.atum.reader.exceptions.RequestException.NoDataException
import za.co.absa.atum.reader.result.GroupedPage.GroupPageRoller
import za.co.absa.atum.reader.result.Page.PageRoller

import scala.collection.immutable.ListMap

case class GroupedPage[K, V, F[_]: MonadError](
                                                items: ListMap[K, Vector[V]],
                                                hasNext: Boolean,
                                                limit: Int,
                                                offset: Long,
                                                private[reader] val pageRoller: GroupPageRoller[K, V, F]
                                    ) extends AbstractPage[Map[K, Vector[V]], F] {

  def apply(key: K): Vector[V] = items(key)

  def map[K1, V1](f: ((K, Vector[V])) => (K1, Vector[V1])): GroupedPage[K1, V1, F] = {
    val newItems = items.map(f)
    val newPageRoller: GroupPageRoller[K1, V1, F] = (limit, offset) => pageRoller(limit, offset).map(_.map(_.map(f)))
    this.copy(items = newItems, pageRoller = newPageRoller)
  }

  def mapValues[B](f: V => B): GroupedPage[K, B, F] = {
    def mapper(item: (K, Vector[V])): (K, Vector[B]) = (item._1, item._2.map(f))

    val newItems = items.map(mapper)
    val newPageRoller: GroupPageRoller[K, B, F] = (limit, offset) => pageRoller(limit, offset).map(_.map(_.mapValues(f)))
    this.copy(items = newItems, pageRoller = newPageRoller)

  }

  def flatten: Page[V, F] = {
    val newItems = items.values.flatten.toVector
    val newPageRoller: PageRoller[V, F] = (limit, offset) => pageRoller(limit, offset).map(_.map(_.flatten))
    Page(
      items = newItems,
      hasNext = hasNext,
      limit = limit,
      offset = offset,
      pageRoller = newPageRoller
    )
  }

  def flatMap[K1, T](f: ((K, Vector[V])) => (K1, Vector[T])): Page[T, F] = {
    map(f).flatten
  }

  def prior(newPageSize: Int): F[RequestResult[GroupedPage[K, V, F]]] = {
    if (hasPrior) {
      val newOffset = (offset - limit).max(0)
      pageRoller(newPageSize, newOffset)
    } else {
      MonadError[F].unit(RequestFail(NoDataException("No prior page")))
    }
  }

  def prior(): F[RequestResult[GroupedPage[K, V, F]]] = prior(limit)

  def next(newPageSize: Int): F[RequestResult[GroupedPage[K, V, F]]] = {
    if (hasNext) {
      pageRoller(newPageSize, offset + limit)
    } else {
      MonadError[F].unit(RequestFail(NoDataException("No next page")))
    }
  }

  def next: F[RequestResult[GroupedPage[K, V, F]]] = next(limit)
}

object GroupedPage {
  type GroupPageRoller[K, V, F[_]] = (Int, Long) => F[RequestResult[GroupedPage[K, V, F]]]
}

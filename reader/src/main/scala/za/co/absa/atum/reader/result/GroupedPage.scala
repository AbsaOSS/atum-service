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

package za.co.absa.atum.reader.result

import sttp.monad.MonadError
import sttp.monad.syntax._
import za.co.absa.atum.reader.core.RequestResult.{RequestFail, RequestResult}
import za.co.absa.atum.reader.exceptions.RequestException.NoDataException
import za.co.absa.atum.reader.result.GroupedPage.GroupPageRoller

import scala.collection.immutable.ListMap

case class GroupedPage[K, V, F[_]: MonadError](
                                                items: ListMap[K, Vector[V]],
                                                hasNext: Boolean,
                                                limit: Int,
                                                pageStart: Long,
                                                pageEnd: Long,
                                                private[reader] val pageRoller: GroupPageRoller[K, V, F]
                                    ) extends AbstractPage[Map[K, Vector[V]], F] {

  def apply(key: K): Vector[V] = items(key)
  def keys: Iterable[K] = items.keys
  def groupCount: Int = items.size

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

  def prior(newPageSize: Int): F[RequestResult[GroupedPage[K, V, F]]] = {
    if (hasPrior) {
      val newOffset = (pageStart - limit).max(0)
      pageRoller(newPageSize, newOffset)
    } else {
      MonadError[F].unit(RequestFail(NoDataException("No prior page")))
    }
  }

  def prior: F[RequestResult[GroupedPage[K, V, F]]] = prior(limit)

  def next(newPageSize: Int): F[RequestResult[GroupedPage[K, V, F]]] = {
    if (hasNext) {
      pageRoller(newPageSize, pageStart + limit)
    } else {
      MonadError[F].unit(RequestFail(NoDataException("No next page")))
    }
  }

  def next: F[RequestResult[GroupedPage[K, V, F]]] = next(limit)

  def +(other: GroupedPage[K, V, F]): GroupedPage[K, V, F] = {
    val newItems = other.items.foldLeft(items) { case (acc, (k, v)) =>
      if (acc.contains(k)) {
        acc.updated(k, acc(k) ++ v)
      } else {
        acc + (k -> v)
      }
    }
    val newHasNext = hasNext && other.hasNext
    val newPageStart = pageStart min other.pageStart
    val newPageEnd = pageEnd max other.pageEnd
    this.copy(items = newItems, hasNext = newHasNext, pageStart = newPageStart, pageEnd = newPageEnd)
  }
}

object GroupedPage {
  type GroupPageRoller[K, V, F[_]] = (Int, Long) => F[RequestResult[GroupedPage[K, V, F]]]
}

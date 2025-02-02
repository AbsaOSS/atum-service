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
import za.co.absa.atum.reader.core.RequestResult.{RequestFail, RequestPageResultOps, RequestResult}
import za.co.absa.atum.reader.exceptions.RequestException.NoDataException
import za.co.absa.atum.reader.result.GroupedPage.GroupPageRoller
import za.co.absa.atum.reader.result.Page.PageRoller

import scala.collection.immutable.ListMap

case class Page[T, F[_]: MonadError](
                                      items: Vector[T],
                                      hasNext: Boolean,
                                      limit: Int,
                                      pageStart: Long,
                                      pageEnd: Long,
                                      private[reader] val pageRoller: PageRoller[T, F]
                                    ) extends AbstractPage[Vector[T], F] {

  def apply(index: Int): T = items(index)

  def map[B](f: T => B): Page[B, F] = {
    val newItems = items.map(f)
    val newPageRoller: PageRoller[B, F] = (limit, offset) => pageRoller(limit, offset).map(_.pageMap(f))
    this.copy(items = newItems, pageRoller = newPageRoller)
  }

  def prior(newPageSize: Int): F[RequestResult[Page[T, F]]] = {
    if (hasPrior) {
      val newOffset = (pageStart - newPageSize).max(0)
      pageRoller(newPageSize, newOffset)
    } else {
      MonadError[F].unit(RequestFail(NoDataException("No prior page")))
    }
  }

  def prior(): F[RequestResult[Page[T, F]]] = prior(limit)

  def next(newPageSize: Int): F[RequestResult[Page[T, F]]] = {
    if (hasNext) {
      pageRoller(newPageSize, pageStart + pageSize)
    } else {
      MonadError[F].unit(RequestFail(NoDataException("No next page")))
    }
  }

  def next: F[RequestResult[Page[T, F]]] = next(limit)

  def +(other: Page[T, F]): Page[T, F] = {
    val newItems = items ++ other.items
    val newPageStart = pageStart min other.pageStart
    val newPageEnd = pageEnd max other.pageEnd
    val newHasNext = hasNext && other.hasNext
    this.copy(items = newItems, hasNext = newHasNext, pageStart = newPageStart, pageEnd = newPageEnd)
  }

  def groupBy[K](f: T => K): GroupedPage[K, T, F] = {
    val (newItems, itemsCounts) = items.foldLeft(ListMap.empty[K, Vector[T]], 0) { case ((groupsAcc, count), item) =>
      val k = f(item)
      (groupsAcc.updated(k, groupsAcc.getOrElse(k, Vector.empty) :+ item), count + 1)
    }
    val newPageRoller: GroupPageRoller[K, T, F] = (limit, offset) =>
      pageRoller(limit, offset)
        .map(_.map(_.groupBy(f)))

    GroupedPage(
      newItems,
      hasNext,
      limit,
      pageStart,
      pageEnd,
      newPageRoller
    )
  }
}

object Page {
  type PageRoller[T, F[_]] = (Int, Long) => F[RequestResult[Page[T, F]]]
}

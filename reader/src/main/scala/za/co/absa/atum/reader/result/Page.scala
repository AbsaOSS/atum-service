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
import za.co.absa.atum.reader.basic.RequestResult.{RequestFail, RequestPageResultOps, RequestResult}
import za.co.absa.atum.reader.exceptions.RequestException.NoDataException
import za.co.absa.atum.reader.implicits.VectorImplicits.VectorEnhancements
import za.co.absa.atum.reader.result.GroupedPage.GroupPageRoller
import za.co.absa.atum.reader.result.Page.PageRoller

import scala.collection.immutable.ListMap

case class Page[T, F[_]: MonadError](
                                      items: Vector[T],
                                      hasNext: Boolean,
                                      limit: Int,
                                      offset: Long,
                                      private[reader] val pageRoller: PageRoller[T, F]
                                    ) extends AbstractPage[Vector[T], F] {

  def apply(index: Int): T = items(index)

  def map[B](f: T => B): Page[B, F] = {
    val newItems = items.map(f)
    val newPageRoller: PageRoller[B, F] = (limit, offset) => pageRoller(limit, offset).map(_.pageMap(f))
    this.copy(items = newItems, pageRoller = newPageRoller)
  }

  def groupBy[K](f: T => K): GroupedPage[K, T, F] = {
    val newItems = items.foldLeft(ListMap.empty[K, Vector[T]]) { (acc, x) =>
      val k = f(x)
      acc.updated(k, acc.getOrElse(k, Vector.empty) :+ x)
    }
    val newPageRoller: GroupPageRoller[K, T, F] = (limit, offset) =>
      pageRoller(limit, offset)
        .map(_.map(_.groupBy(f)))

    GroupedPage(
      newItems,
      hasNext,
      limit,
      offset,
      newPageRoller
    )
  }

  def prior(newPageSize: Int): F[RequestResult[Page[T, F]]] = {
    if (hasPrior) {
      val newOffset = (offset - limit).max(0)
      pageRoller(newPageSize, newOffset)
    } else {
      MonadError[F].unit(RequestFail(NoDataException("No prior page")))
    }
  }

  def prior(): F[RequestResult[Page[T, F]]] = prior(limit)

  def next(newPageSize: Int): F[RequestResult[Page[T, F]]] = {
    if (hasNext) {
      pageRoller(newPageSize, offset + limit)
    } else {
      MonadError[F].unit(RequestFail(NoDataException("No next page")))
    }
  }

  def next: F[RequestResult[Page[T, F]]] = next(limit)
}

object Page {
  type PageRoller[T, F[_]] = (Int, Long) => F[RequestResult[Page[T, F]]]
}

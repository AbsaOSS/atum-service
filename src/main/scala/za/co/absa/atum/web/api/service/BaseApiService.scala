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

package za.co.absa.atum.web.api.service

import za.co.absa.atum.web.api.NotFoundException
import za.co.absa.atum.web.model.BaseApiModel

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait BaseApiService[C <: BaseApiModel] {

  def getList(limit: Int, offset: Int): Future[List[C]]

  def add(item: C): Future[UUID]

  def getById(uuid: UUID): Future[Option[C]] // todo open just return Future[C] or throw?

  def exists(uuid: UUID): Future[Boolean] = {
    // default implementation that will work, but specific services may override it for optimization
    getById(uuid).map(_.nonEmpty)
    // todo if getById only returns Future[C], consider default impl such as:
    //  getById(uuid).transformWith {
    //    case Success(_) => Future.successful(true)
    //    case Failure(_) => Future.successful(false)
    //  }
  }

  /**
   * Finds entity by `id`` and applies method `fn`. Throws NotFoundException when not found
   * @param id
   * @param fn
   * @tparam S
   * @return
   */
  def withExistingEntity[S](id: UUID)(fn: C => S): Future[S] = {
    withExistingEntityF(id) { c =>
      Future {fn(c)}
    }
  }

  /**
   * Same as za.co.absa.atum.web.api.service.BaseApiService#withExistingEntity(java.util.UUID, scala.Function1), but
   * you may pass `fn` that returns a Future.
   * @param id
   * @param fn
   * @tparam S
   * @return
   */
  def withExistingEntityF[S](id: UUID)(fn: C => Future[S]): Future[S] = {
    getById(id) flatMap {
      case None => Future.failed(
        throw NotFoundException(s"$entityName referenced by id=${id} was not found.")
      )
      case Some(existingEntity) => fn(existingEntity)
    }
  }

  protected def entityName: String

}

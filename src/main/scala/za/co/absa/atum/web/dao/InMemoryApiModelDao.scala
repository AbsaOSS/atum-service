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

package za.co.absa.atum.web.dao

import za.co.absa.atum.web.model.BaseApiModel

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait InMemoryApiModelDao[T <: BaseApiModel] extends ApiModelDao[T] {

  private val inmemory: ConcurrentMap[UUID, T] = new ConcurrentHashMap[UUID, T]()

  def getList(limit: Int, offset: Int, filter: T => Boolean): Future[List[T]] = Future {
    inmemory.values.asScala
      .filter(filter)
      .slice(offset, offset + limit).toList // limiting, todo pagination or similar
  }

  def getById(uuid: UUID): Future[Option[T]] = Future {
    Option(inmemory.get(uuid))
  }

  def add(entity: T): Future[UUID] = {
    entity.id match {
      case Some(_) => Future.failed(new IllegalArgumentException(f"Newly persisted ${entity.entityName} must not have id, the persistence will assign one!"))
      case None =>
        // persistence impl: supplies the ID internally:
        val newId = UUID.randomUUID()
        val idEntity = entity.withId(newId)

        Option(inmemory.putIfAbsent(newId, idEntity.asInstanceOf[T])) match { // todo use F-bounded type polymorphism instead?
          case None => Future.successful(newId)
          case Some(_) => Future.failed(throw new IllegalStateException(s"Entity with id $newId already exists, use editing to update it"))
        }
    }
  }

  def update(entity: T): Future[Boolean] = {
    entity.id match {
      case None => Future.failed(new IllegalArgumentException(s"Updated ${entity.entityName} must have id!"))
      case Some(id) =>
        Option(inmemory.replace(id, entity)) match {
          case None => Future.failed(new IllegalStateException(s"Expected to find previous persisted version of ${entity.entityName} by id=$id, but found none."))
          case Some(_) => Future.successful(true)
        }
    }
  }

}

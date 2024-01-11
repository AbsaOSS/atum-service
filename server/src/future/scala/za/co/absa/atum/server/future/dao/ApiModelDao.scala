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

package za.co.absa.atum.server.future.dao

import za.co.absa.atum.server.future.model.BaseApiModel

import java.util.UUID
import scala.concurrent.Future

trait ApiModelDao[T <: BaseApiModel] {

  def getList(limit: Int, offset: Int, filter: T => Boolean = _ => true): Future[List[T]]

  def getById(uuid: UUID): Future[Option[T]]

  def add(entity: T): Future[UUID]

  def update(entity: T): Future[Boolean]

}
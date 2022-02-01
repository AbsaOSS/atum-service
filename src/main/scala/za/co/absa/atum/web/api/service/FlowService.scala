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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.atum.web.model.Flow

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Service
class FlowService @Autowired()() extends BaseApiService[Flow] {
  // temporary storage // redo with db-persistence layer when ready
  val inmemory: mutable.Map[UUID, Flow] = scala.collection.mutable.Map[UUID, Flow]()

  def getList(limit: Int, offset: Int): Future[List[Flow]] = Future {
    inmemory.values.drop(offset).take(limit).toList // limiting, todo pagination or similar
  }

  def add(f: Flow): Future[UUID] = Future {
    require(f.id.isEmpty)

    // persistence impl: supplies the ID internally:
    val newId = UUID.randomUUID()
    inmemory.put(newId, f.withId(newId)) // assuming the persistence would throw on error
    newId
  }

  def getById(uuid: UUID): Future[Option[Flow]] = Future {
    inmemory.get(uuid)
  }

}
//
//// todo when generalizing into EntityService, use these as default
//object FlowService {
//  val DefaultLimit: Int = 20
//  val DefaultOffset: Int = 0
//}

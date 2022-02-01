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

package za.co.absa.atum.web.api.controller

import org.springframework.http.{HttpStatus, ResponseEntity}
import org.springframework.web.bind.annotation._
import za.co.absa.atum.web.api.NotFoundException
import za.co.absa.atum.web.api.implicits._
import za.co.absa.atum.web.api.payload.MessagePayload
import za.co.absa.atum.web.api.service.BaseApiService
import za.co.absa.atum.web.model.BaseApiModel

import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.{Optional, UUID}
import scala.concurrent.ExecutionContext.Implicits.global

abstract class BaseApiController[C <: BaseApiModel](baseApiService: BaseApiService[C]) {

  @GetMapping(Array(""))
  @ResponseStatus(HttpStatus.OK)
  def getList(@RequestParam limit: Optional[Int], @RequestParam offset: Optional[Int]): CompletableFuture[Seq[C]] = {
    val actualLimit = limit.toScalaOption.getOrElse(BaseApiController.DefaultLimit)
    val actualOffset = offset.toScalaOption.getOrElse(BaseApiController.DefaultOffset)
    baseApiService.getList(limit = actualLimit, offset = actualOffset)
  }

  @GetMapping(Array("/{id}"))
  @ResponseStatus(HttpStatus.OK)
  def getOne(@PathVariable id: UUID): CompletableFuture[C] = {
    baseApiService.getById(id).map{
      case Some(flow) => flow
      case None => throw NotFoundException(s"No entity found by id $id")
    }
  }

  @PostMapping(Array(""))
  @ResponseStatus(HttpStatus.CREATED)
  def create(@RequestBody item: C): CompletableFuture[ResponseEntity[MessagePayload]] = {
    baseApiService.add(item).map { id =>
      val location: URI = new URI(s"/api/controlmeasures/${id}") // todo generalize location somehow
      ResponseEntity.created(location)
        .body[MessagePayload](MessagePayload(s"Successfully created control measure with id $id"))
    }
  }
}

object BaseApiController {
  val DefaultLimit: Int = 20
  val DefaultOffset: Int = 0
}

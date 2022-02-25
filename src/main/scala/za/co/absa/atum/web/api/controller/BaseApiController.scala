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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import za.co.absa.atum.web.api.controller.BaseApiController.{DefaultLimit, DefaultOffset, LimitParamName, OffsetParamName}
import za.co.absa.atum.web.api.implicits._
import za.co.absa.atum.web.api.payload.MessagePayload
import za.co.absa.atum.web.api.service.BaseApiService
import za.co.absa.atum.web.model.BaseApiModel

import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.{Optional, UUID}
import javax.servlet.http.HttpServletRequest
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class BaseApiController[C <: BaseApiModel](baseApiService: BaseApiService[C]) {

  @GetMapping(Array(""))
  @ResponseStatus(HttpStatus.OK)
  def getList(@RequestParam allParams: java.util.Map[String,String]): CompletableFuture[Seq[C]] = {
    val scalaParams = allParams.asScala.toMap
    baseApiService.getList(limit = getLimitFromParams(scalaParams), offset = getOffsetFromParams(scalaParams))
  }

  @GetMapping(Array("/{id}"))
  @ResponseStatus(HttpStatus.OK)
  def getOne(@PathVariable id: UUID): CompletableFuture[C] = {
    baseApiService.withExistingEntity(id){entity => entity}
  }

  @PostMapping(Array(""))
  @ResponseStatus(HttpStatus.CREATED)
  def create(@RequestBody item: C, request: HttpServletRequest): CompletableFuture[ResponseEntity[MessagePayload]] = {
    baseApiService.add(item).map { id =>
      val location: URI = ServletUriComponentsBuilder
        .fromRequest(request)
        .path("/{id}")
        .buildAndExpand(id)
        .toUri() // will create location e.g. /api/controlmeasures/someIdHere

      ResponseEntity.created(location)
        .body[MessagePayload](MessagePayload(s"Successfully created ${baseApiService.entityName} with id $id"))
    }
  }

  val entityName: String = baseApiService.entityName

  protected def getLimitFromParams(params: Map[String, String], defaultValue: Int = DefaultLimit): Int = {
    params.get(LimitParamName).map(_.toInt).getOrElse(defaultValue)
  }

  protected def getOffsetFromParams(params: Map[String, String], defaultValue: Int = DefaultOffset): Int = {
    params.get(OffsetParamName).map(_.toInt).getOrElse(defaultValue)
  }
}

object BaseApiController {
  val LimitParamName = "limit"
  val DefaultLimit: Int = 20

  val OffsetParamName = "offset"
  val DefaultOffset: Int = 0

}

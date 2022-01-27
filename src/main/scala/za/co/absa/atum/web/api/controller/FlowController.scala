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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, ResponseEntity}
import org.springframework.web.bind.annotation.{GetMapping, PathVariable, PostMapping, RequestBody, RequestMapping, RequestParam, ResponseBody, ResponseStatus, RestController}
import za.co.absa.atum.web.api.NotFoundException
import za.co.absa.atum.web.api.implicits._
import za.co.absa.atum.web.api.payload.MessagePayload
import za.co.absa.atum.web.api.service.FlowService
import za.co.absa.atum.web.model.Flow

import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.{Optional, UUID}
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping(Array("/api/flows"))
class FlowController @Autowired()(flowService: FlowService) {

  @GetMapping(Array("/"))
  @ResponseStatus(HttpStatus.OK)
  def getList(@RequestParam limit: Optional[Int], @RequestParam offset: Optional[Int]): CompletableFuture[Seq[Flow]] = {
    val actualLimit = limit.toScalaOption.getOrElse(FlowService.DefaultLimit)
    val actualOffset = offset.toScalaOption.getOrElse(FlowService.DefaultOffset)
    flowService.getList(limit = actualLimit, offset = actualOffset)
  }

  @GetMapping(Array("/{id}"))
  @ResponseStatus(HttpStatus.OK)
  def getVersionDetail(@PathVariable id: UUID): CompletableFuture[Flow] = {
    flowService.get(id).map{
      case Some(flow) => flow
      case None => throw NotFoundException(s"No flow by id $id")
    }
  }

  @PostMapping(Array("/"))
  @ResponseStatus(HttpStatus.CREATED)
  def create(@RequestBody scalaItem: Flow): CompletableFuture[ResponseEntity[MessagePayload]] = {
    flowService.add(scalaItem).map { id =>
      val location: URI = new URI(s"/api/flows/${id}")
      ResponseEntity.created(location)
        .body[MessagePayload](MessagePayload(s"Successfully created flow with id $id"))
    }
  }

}

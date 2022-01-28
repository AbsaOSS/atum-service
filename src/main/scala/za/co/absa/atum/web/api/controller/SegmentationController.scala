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
import org.springframework.web.bind.annotation._
import za.co.absa.atum.web.api.NotFoundException
import za.co.absa.atum.web.api.implicits._
import za.co.absa.atum.web.api.payload.MessagePayload
import za.co.absa.atum.web.api.service.{FlowService, SegmentationService}
import za.co.absa.atum.web.model.{Flow, Segmentation}

import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.{Optional, UUID}
import scala.concurrent.ExecutionContext.Implicits.global

@RestController
@RequestMapping(Array("/api/segmentations"))
class SegmentationController @Autowired()(segmentationService: SegmentationService) {

  @GetMapping(Array(""))
  @ResponseStatus(HttpStatus.OK)
  def getList(@RequestParam limit: Optional[Int], @RequestParam offset: Optional[Int]): CompletableFuture[Seq[Segmentation]] = {
    val actualLimit = limit.toScalaOption.getOrElse(FlowService.DefaultLimit) // todo generalize
    val actualOffset = offset.toScalaOption.getOrElse(FlowService.DefaultOffset)
    segmentationService.getList(limit = actualLimit, offset = actualOffset)
  }

  @GetMapping(Array("/{id}"))
  @ResponseStatus(HttpStatus.OK)
  def getOne(@PathVariable id: UUID): CompletableFuture[Segmentation] = {
    segmentationService.get(id).map{
      case Some(flow) => flow
      case None => throw NotFoundException(s"No segmentation by id $id")
    }
  }

  @PostMapping(Array(""))
  @ResponseStatus(HttpStatus.CREATED)
  def create(@RequestBody item: Segmentation): CompletableFuture[ResponseEntity[MessagePayload]] = {
    segmentationService.add(item).map { id =>
      val location: URI = new URI(s"/api/segmentations/${id}")
      ResponseEntity.created(location)
        .body[MessagePayload](MessagePayload(s"Successfully created segmentation with id $id"))
    }
  }

}

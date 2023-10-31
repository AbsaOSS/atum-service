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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import za.co.absa.atum.web.api.implicits._
import za.co.absa.atum.web.api.payload.MessagePayload
import za.co.absa.atum.web.api.service.ControlMeasureService
import za.co.absa.atum.web.model._

import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.{Optional, UUID}
import javax.servlet.http.HttpServletRequest
import scala.concurrent.ExecutionContext.Implicits.global

@RestController
@RequestMapping(Array("/api/controlmeasures"))
class ControlMeasureController @Autowired()(controlMeasureService: ControlMeasureService)
  extends BaseApiController(controlMeasureService) {

  @PutMapping(Array("/{id}"))
  @ResponseStatus(HttpStatus.OK)
  def update(@PathVariable id: UUID, @RequestBody controlMeasure: ControlMeasure): CompletableFuture[MessagePayload] = {
    require(controlMeasure.id.isEmpty, "A ControlMeasure update payload must have no id (it is given in the URL)!")
    val cm = controlMeasure.withId(id)
    controlMeasureService.update(cm).map(_ => MessagePayload(s"Successfully updated ControlMeasure id=$id"))
  }

  @GetMapping(Array("/{flowid}/{segid}"))
  @ResponseStatus(HttpStatus.OK)
  def getListByFlowAndSegIds(@PathVariable flowId: UUID, @PathVariable segId: UUID,
                             @RequestParam limit: Optional[String], @RequestParam offset: Optional[String]
                            ): CompletableFuture[Seq[ControlMeasure]] = {
    val actualLimit = limit.toScalaOption.map(_.toInt).getOrElse(BaseApiController.DefaultLimit)
    val actualOffset = offset.toScalaOption.map(_.toInt).getOrElse(BaseApiController.DefaultOffset)
    controlMeasureService.getListByFlowAndSegIds(flowId, segId, actualLimit, actualOffset)
  }

  @GetMapping(Array("/{id}/metadata"))
  @ResponseStatus(HttpStatus.OK)
  def getMetadata(@PathVariable id: UUID): CompletableFuture[ControlMeasureMetadata] = {
    controlMeasureService.withExistingEntity(id)(_.metadata)
  }

  @PutMapping(Array("/{id}/metadata"))
  @ResponseStatus(HttpStatus.OK)
  def updateMetadata(@PathVariable id: UUID, @RequestBody metadata: ControlMeasureMetadata): CompletableFuture[MessagePayload] = {
    controlMeasureService.updateMetadata(id, metadata)
      .map(_ => MessagePayload(s"Successfully updated metadata of ControlMeasure id=$id"))
  }

  @GetMapping(Array("/{id}/checkpoints"))
  @ResponseStatus(HttpStatus.OK)
  def getCheckpoint(@PathVariable id: UUID): CompletableFuture[List[Checkpoint]] = {
    controlMeasureService.getCheckpointList(id)
  }

  @PostMapping(Array("/{cmId}/checkpoints"))
  @ResponseStatus(HttpStatus.CREATED)
  def create(@PathVariable cmId: UUID, @RequestBody cp: Checkpoint, request: HttpServletRequest): CompletableFuture[ResponseEntity[MessagePayload]] = {
    controlMeasureService.addCheckpoint(cmId, cp).map { cpId =>
      val location: URI = ServletUriComponentsBuilder
        .fromRequest(request)
        .path("/{cpId}")
        .buildAndExpand(cpId)
        .toUri // will create location e.g. /api/controlmeasures/{cmId}/checkpoints/{cpId}

      ResponseEntity.created(location)
        .body[MessagePayload](MessagePayload(s"Successfully added Checkpoint id=$cpId, for ControlMeasure id=$cmId"))
    }
  }

  @GetMapping(Array("/{cmId}/checkpoints/{cpId}"))
  @ResponseStatus(HttpStatus.OK)
  def getOneCheckpoint(@PathVariable cmId: UUID, @PathVariable cpId: UUID): CompletableFuture[Checkpoint] = {
    controlMeasureService.getCheckpointById(cmId, cpId)
  }

  // todo change PUTs payload to have all-optional fields so that updates can be partial only
  // (not overwrite/unnecessarily repeat the whole entity)

  @PutMapping(Array("/{cmId}/checkpoints/{cpId}"))
  @ResponseStatus(HttpStatus.OK)
  def updateCheckpoint(@PathVariable cmId: UUID, @PathVariable cpId: UUID, @RequestBody cpUpdate: CheckpointUpdate): CompletableFuture[MessagePayload] = {
    controlMeasureService.updateCheckpoint(cmId, cpId, cpUpdate).map(_ => MessagePayload(s"Successfully updated Checkpoint id=$cpId"))
  }

  @GetMapping(Array("/{cmId}/checkpoints/{cpId}/measurements"))
  @ResponseStatus(HttpStatus.OK)
  def getMeasurements(@PathVariable cmId: UUID, @PathVariable cpId: UUID): CompletableFuture[List[Measurement]] = {
    controlMeasureService.getMeasurements(cmId, cpId)
  }

  @PostMapping(Array("/{cmId}/checkpoints/{cpId}/measurements"))
  @ResponseStatus(HttpStatus.CREATED)
  def createMeasurement(@PathVariable cmId: UUID, @PathVariable cpId: UUID, @RequestBody measurement: Measurement, request: HttpServletRequest): CompletableFuture[ResponseEntity[MessagePayload]] = {
    controlMeasureService.addMeasurement(cmId, cpId, measurement).map { _ =>
      val location: URI = ServletUriComponentsBuilder
        .fromRequest(request)
        .build()
        .toUri

      ResponseEntity.created(location)
        .body[MessagePayload](MessagePayload(s"Successfully added Measurement to CP id=$cpId, for ControlMeasure id=$cmId"))
    }
  }

}

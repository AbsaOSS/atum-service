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
import za.co.absa.atum.web.api.controller.BaseApiController.{LimitParamName, OffsetParamName}
import za.co.absa.atum.web.api.controller.FlowController.FlowDefParamName
import za.co.absa.atum.web.api.implicits._
import za.co.absa.atum.web.api.payload.MessagePayload
import za.co.absa.atum.web.api.service.FlowService
import za.co.absa.atum.web.model._

import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.servlet.http.HttpServletRequest
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._

@RestController
@RequestMapping(Array("/api/flows"))
class FlowController @Autowired()(flowService: FlowService) extends BaseApiController(flowService) {

  @PutMapping(Array("/{id}"))
  @ResponseStatus(HttpStatus.OK)
  def update(@PathVariable id: UUID, @RequestBody flow: Flow): CompletableFuture[MessagePayload] = {
    require(flow.id.isEmpty, s"A $entityName update payload must have no id (it is given in the URL)!")
    val cm = flow.withId(id)
    flowService.update(cm).map(_ => MessagePayload(s"Successfully updated $entityName id=$id"))
  }

  @GetMapping(Array(""))
  @ResponseStatus(HttpStatus.OK)
  override def getList(@RequestParam allParams: java.util.Map[String,String]): CompletableFuture[Seq[Flow]] = {
    // this REST mapping is overridden from BaseApiController - using java.util.Map for a common param interface
    val scalaParams = allParams.asScala.toMap
    scalaParams.get(FlowDefParamName).map(UUID.fromString) match {
      case None => super.getList(allParams) // default impl for no flowDef filtering
      case Some(flowDefId) =>
        flowService.getListByFlowDefId(flowDefId, limit = getLimitFromParams(scalaParams), offset = getOffsetFromParams(scalaParams))
    }
  }

  @GetMapping(Array("/{id}/metadata"))
  @ResponseStatus(HttpStatus.OK)
  def getMetadata(@PathVariable id: UUID): CompletableFuture[FlowMetadata] = {
    flowService.withExistingEntity(id)(_.metadata)
  }

  @PutMapping(Array("/{id}/metadata"))
  @ResponseStatus(HttpStatus.OK)
  def updateMetadata(@PathVariable id: UUID, @RequestBody metadata: FlowMetadata): CompletableFuture[MessagePayload] = {
    flowService.updateMetadata(id, metadata)
      .map(_ => MessagePayload(s"Successfully updated metadata of ControlMeasure id=$id"))
  }

  @GetMapping(Array("/{id}/checkpoints"))
  @ResponseStatus(HttpStatus.OK)
  def getCheckpoint(@PathVariable id: UUID): CompletableFuture[List[Checkpoint]] = {
    flowService.getCheckpointList(id)
  }

  @PostMapping(Array("/{flowId}/checkpoints"))
  @ResponseStatus(HttpStatus.CREATED)
  def create(@PathVariable flowId: UUID, @RequestBody cp: Checkpoint, request: HttpServletRequest): CompletableFuture[ResponseEntity[MessagePayload]] = {
    flowService.addCheckpoint(flowId, cp).map { cpId =>
      val location: URI = ServletUriComponentsBuilder
        .fromRequest(request)
        .path("/{cpId}")
        .buildAndExpand(cpId)
        .toUri() // will create location e.g. /api/flows/{flowId}/checkpoints/{cpId}

      ResponseEntity.created(location)
        .body[MessagePayload](MessagePayload(s"Successfully added Checkpoint id=$cpId, for $entityName id=$flowId"))
    }
  }

  @GetMapping(Array("/{flowId}/checkpoints/{cpId}"))
  @ResponseStatus(HttpStatus.OK)
  def getOneCheckpoint(@PathVariable flowId: UUID, @PathVariable cpId: UUID): CompletableFuture[Checkpoint] = {
    flowService.getCheckpointById(flowId, cpId)
  }

  // todo change PUTs payload to have all-optional fields so that updates can be partial only
  // (not overwrite/unnecessarily repeat the whole entity)

  @PutMapping(Array("/{flowId}/checkpoints/{cpId}"))
  @ResponseStatus(HttpStatus.OK)
  def updateCheckpoint(@PathVariable flowId: UUID, @PathVariable cpId: UUID, @RequestBody cpUpdate: CheckpointUpdate): CompletableFuture[MessagePayload] = {
    flowService.updateCheckpoint(flowId, cpId, cpUpdate).map(_ => MessagePayload(s"Successfully updated Checkpoint id=$cpId"))
  }

  @GetMapping(Array("/{flowId}/checkpoints/{cpId}/measurements"))
  @ResponseStatus(HttpStatus.OK)
  def getMeasurements(@PathVariable flowId: UUID, @PathVariable cpId: UUID): CompletableFuture[List[Measurement]] = {
    flowService.getMeasurements(flowId, cpId)
  }

  @PostMapping(Array("/{flowId}/checkpoints/{cpId}/measurements"))
  @ResponseStatus(HttpStatus.CREATED)
  def createMeasurement(@PathVariable flowId: UUID, @PathVariable cpId: UUID, @RequestBody measurement: Measurement, request: HttpServletRequest): CompletableFuture[ResponseEntity[MessagePayload]] = {
    flowService.addMeasurement(flowId, cpId, measurement).map { _ =>
      val location: URI = ServletUriComponentsBuilder
        .fromRequest(request)
        .build()
        .toUri()

      ResponseEntity.created(location)
        .body[MessagePayload](MessagePayload(s"Successfully added Measurement to CP id=$cpId, for $entityName id=$flowId"))
    }
  }

}

object FlowController {
  val FlowDefParamName = "flowdef"
}

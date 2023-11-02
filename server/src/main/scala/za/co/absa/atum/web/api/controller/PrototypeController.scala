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
import za.co.absa.atum.model.CheckpointFilterCriteria
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.web.api.service.DatabaseService


@RestController
@RequestMapping(Array("/api"))
class PrototypeController @Autowired()(databaseService: DatabaseService){

  /**
   * Saves a checkpoint.
   *
   * @param checkpoint The checkpoint to save.
   * @return A ResponseEntity with the status code CREATED.
   */
  @PostMapping(Array("/v1/checkpoint"))
  def saveCheckpoint(@RequestBody checkpoint: CheckpointDTO): ResponseEntity[Unit] = {
    databaseService.saveCheckpoint(checkpoint)
    ResponseEntity.status(HttpStatus.CREATED).build()
  }

  @GetMapping(Array("/v1/checkpoint"))
  @ResponseStatus(HttpStatus.OK)
  def readCheckpoint(@RequestBody filterCriteria: CheckpointFilterCriteria): ResponseEntity[ResponseBody] = {
    databaseService.readCheckpoint(filterCriteria)
    ResponseEntity.status(HttpStatus.OK).body()
  }
}

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

package za.co.absa.atum.server.api.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation._
import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.api.service.DatabaseService

import scala.concurrent.{ExecutionContext, Future}
import za.co.absa.atum.server.api.implicits._

import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping(Array("/api/v1"))
class PrototypeController @Autowired()(databaseService: DatabaseService){

  implicit val executionContext: ExecutionContext = databaseService.executionContext

  /**
   * Creates a checkpoint in a DB.
   *
   * @param checkpoint The checkpoint to create.
   * @return A ResponseEntity with the status code CREATED.
   */
  @PostMapping(path = Array("/createCheckpoint"))
  @ResponseStatus(HttpStatus.CREATED)
  def createCheckpoint(@RequestBody checkpoint: CheckpointDTO): CompletableFuture[CheckpointDTO] = {
    databaseService.saveCheckpoint(checkpoint)
  }

  /**
   * Creates a partitioning in a DB and returns an Atum Context out of it, or return an existing one if it already
   * exists in a DB.
   *
   * @param partitioning DTO (JSON-like) object containing fields that will be used for creating a partitioning.
   * @return A new AtumContext object that uses newly obtained partitioning.
   */
  @PostMapping(Array("/createPartitioning"))
  @ResponseStatus(HttpStatus.OK)
  def createPartitioning(@RequestBody partitioning: PartitioningSubmitDTO): CompletableFuture[AtumContextDTO] = {
    // TODO #120, get measures from DB, this solution is temporary - we need record count always for now
    val measures: Set[MeasureDTO] = Set(MeasureDTO("count", Seq("*")))

    val additionalData = AdditionalDataDTO(additionalData = Map.empty)

    for {
      partitioningFuture <- databaseService.createPartitioningIfNotExists(partitioning)
    } yield AtumContextDTO(partitioningFuture.partitioning, measures, additionalData)
  }

}

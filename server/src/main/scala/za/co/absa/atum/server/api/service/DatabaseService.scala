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

package za.co.absa.atum.server.api.service

import org.springframework.stereotype.Service
<<<<<<< HEAD
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AtumContextDTO, CheckpointDTO, MeasureDTO, PartitionDTO, PartitioningCreationDTO}
=======
import za.co.absa.atum.model.dto.{CheckpointDTO, PartitionDTO, PartitioningDTO}
>>>>>>> d9fcb0d2c85e2893d1ce37926f08fc99881d0d06
import za.co.absa.atum.server.api.implicits.scalaToJavaFuture
import za.co.absa.atum.server.api.provider.PostgresAccessProvider

import java.util.concurrent.CompletableFuture
import scala.concurrent.ExecutionContext

@Service
class DatabaseService (@Autowired postgresAccessProvider: PostgresAccessProvider = new PostgresAccessProvider){

//  private val postgresAccessProvider: PostgresAccessProvider = new PostgresAccessProvider

  /** This service function saves the checkpoint into the database. */
  def saveCheckpoint(checkpoint: CheckpointDTO): CompletableFuture[CheckpointDTO] = {
    implicit val executionContext: ExecutionContext = postgresAccessProvider.executor
    for {
      _ <- postgresAccessProvider.runs.writeCheckpoint(checkpoint)
    } yield checkpoint
  }

  /**
   * Function to create partitioning based on the provided fields
   *
   * @param partitioningInfo json information to be added while creating a partition
   */
  def createPartitioningIfNotExists(partitioningInfo: PartitioningDTO): AtumContextDTO = {
    // Todo - db function call to be implemented in #23
    val partitioning = Seq()
    val measures = Set.empty[MeasureDTO]
    val additionalData = AdditionalDataDTO(additionalData = Map.empty)

    AtumContextDTO(partitioning, measures, additionalData)
  }
}

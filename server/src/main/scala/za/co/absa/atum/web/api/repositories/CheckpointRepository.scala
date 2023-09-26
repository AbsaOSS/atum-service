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

package za.co.absa.atum.web.api.repositories

import za.co.absa.atum.model.Checkpoint
import za.co.absa.atum.web.api.service.CheckpointService


trait CheckpointRepository {
  def saveCheckpoint(checkpoint: Checkpoint): Unit
}

class PostgresCheckpointRepository(checkpointService: CheckpointService) extends CheckpointRepository {
  private val db = DatabaseConnection.getConnection()

  override def saveCheckpoint(checkpoint: Checkpoint): Unit = {
    val sqlQuery = "INSERT INTO checkpoint (name, partitioning, processStartTime, processEndTime, measurements) VALUES (?, ?, ?, ?, ?)"
    val preparedStatement = db.prepareStatement(sqlQuery)

    preparedStatement.setString(1, checkpoint.name)
    preparedStatement.setString(2, checkpoint.partitioning.toString)
    preparedStatement.setString(3, checkpoint.processStartTime.toString)
    preparedStatement.setString(4, checkpoint.processEndTime.toString)
    preparedStatement.setObject(5, checkpoint.measurements)
    preparedStatement.executeUpdate()
//    checkpointService.saveCheckpoint(checkpoint)
  }
}


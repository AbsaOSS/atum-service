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

<<<<<<< HEAD:server/src/main/scala/za/co/absa/atum/web/api/service/DatabaseService.scala
package za.co.absa.atum.web.api.service
=======
package za.co.absa.atum.server.api.service
>>>>>>> origin:server/src/main/scala/za/co/absa/atum/server/api/service/DatabaseService.scala

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.atum.model.dto.CheckpointDTO
<<<<<<< HEAD:server/src/main/scala/za/co/absa/atum/web/api/service/DatabaseService.scala
import za.co.absa.atum.web.api.provider.PostgresAccessProvider
=======
import za.co.absa.atum.server.api.implicits.scalaToJavaFuture
import za.co.absa.atum.server.api.provider.PostgresAccessProvider

import java.util.concurrent.CompletableFuture
import scala.concurrent.ExecutionContext
>>>>>>> origin:server/src/main/scala/za/co/absa/atum/server/api/service/DatabaseService.scala

@Service
class DatabaseService @Autowired()() {

  val postgresAccessProvider: PostgresAccessProvider = new PostgresAccessProvider
<<<<<<< HEAD:server/src/main/scala/za/co/absa/atum/web/api/service/DatabaseService.scala
  /**
   * this service function saves the checkpoint into the database.
   * @param checkpoint
   */
  def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    postgresAccessProvider.runs.writeCheckpoint(checkpoint)
=======

  /** This service function saves the checkpoint into the database. */
  def saveCheckpoint(checkpoint: CheckpointDTO): CompletableFuture[CheckpointDTO] = {
    implicit val executionContext: ExecutionContext = postgresAccessProvider.executor
    for {
      _ <- postgresAccessProvider.runs.writeCheckpoint(checkpoint)
    } yield checkpoint
>>>>>>> origin:server/src/main/scala/za/co/absa/atum/server/api/service/DatabaseService.scala
  }

}

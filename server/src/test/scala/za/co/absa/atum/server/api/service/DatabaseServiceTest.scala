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

import org.scalatest.funsuite.AnyFunSuite
import org.mockito.MockitoSugar
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.api.provider.PostgresAccessProvider
import scala.concurrent.Future

class DatabaseServiceTest extends AnyFunSuite with MockitoSugar {

//  test("testSaveCheckpoint should save a checkpoint in the database")  {
//    val mockPostgresAccessProvider = mock[PostgresAccessProvider]
//    val checkpointDTO = mock[CheckpointDTO]
//    val databaseService = new DatabaseService()
//
//    when(mockPostgresAccessProvider.runs.writeCheckpoint(checkpointDTO)).thenReturn(Future.successful(checkpointDTO))
//
//    val results = databaseService.saveCheckpoint(checkpointDTO)
//    println(results)
//    results.complete(checkpointDTO)
//
//  }
//
//  test("testReadCheckpoint") {}
//
//  test("testPostgresAccessProvider") {}

}

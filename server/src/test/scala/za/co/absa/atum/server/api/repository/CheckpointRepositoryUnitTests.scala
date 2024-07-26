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

package za.co.absa.atum.server.api.repository

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpoint
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.TestData
import za.co.absa.db.fadb.exceptions.ErrorInDataException
import za.co.absa.db.fadb.status.FunctionStatus
import zio._
import zio.interop.catz.asyncInstance
import zio.test.Assertion.{failsWithA, isUnit}
import zio.test._

import za.co.absa.db.fadb.status.Row

object CheckpointRepositoryUnitTests extends ZIOSpecDefault with TestData {

  private val writeCheckpointMock: WriteCheckpoint = mock(classOf[WriteCheckpoint])

  when(writeCheckpointMock.apply(checkpointDTO1)).thenReturn(ZIO.right(Row(FunctionStatus(0, "success"), ())))
  when(writeCheckpointMock.apply(checkpointDTO2))
    .thenReturn(ZIO.fail(DatabaseError("Operation 'writeCheckpoint' failed with unexpected error: null")))
  when(writeCheckpointMock.apply(checkpointDTO3)).thenReturn(ZIO.fail(new Exception("boom!")))

  private val writeCheckpointMockLayer = ZLayer.succeed(writeCheckpointMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CheckpointRepositorySuite")(
      suite("WriteCheckpointSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- CheckpointRepository.writeCheckpoint(checkpointDTO1)
          } yield assertTrue(result == ())
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- CheckpointRepository.writeCheckpoint(checkpointDTO2).exit
          } yield assertTrue(result == Exit.fail(DatabaseError("Operation 'writeCheckpoint' failed with unexpected error: null")))
        },
        test("Returns expected DatabaseError") {
          assertZIO(CheckpointRepository.writeCheckpoint(checkpointDTO3).exit)(failsWithA[DatabaseError])
        }
      )
    ).provide(CheckpointRepositoryImpl.layer, writeCheckpointMockLayer)

  }

}

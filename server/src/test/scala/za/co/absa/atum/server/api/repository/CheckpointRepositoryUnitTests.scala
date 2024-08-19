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
import za.co.absa.atum.server.api.database.runs.functions.{WriteCheckpoint, WriteCheckpointV1}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpoint.WriteCheckpointArgs
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.db.fadb.exceptions.DataConflictException
import za.co.absa.db.fadb.status.FunctionStatus
import zio._
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.test._
import za.co.absa.db.fadb.status.Row

object CheckpointRepositoryUnitTests extends ZIOSpecDefault with TestData {

  private val writeCheckpointV1Mock: WriteCheckpointV1 = mock(classOf[WriteCheckpointV1])
  private val writeCheckpointMock: WriteCheckpoint = mock(classOf[WriteCheckpoint])

  when(writeCheckpointV1Mock.apply(checkpointDTO1)).thenReturn(ZIO.right(Row(FunctionStatus(0, "success"), ())))
  when(writeCheckpointV1Mock.apply(checkpointDTO2))
    .thenReturn(ZIO.fail(DataConflictException(FunctionStatus(31, "conflict"))))
  when(writeCheckpointV1Mock.apply(checkpointDTO3)).thenReturn(ZIO.fail(new Exception("boom!")))

  private val partitioningId = 1L

  when(writeCheckpointMock.apply(WriteCheckpointArgs(partitioningId, checkpointV2DTO1)))
    .thenReturn(ZIO.right(Row(FunctionStatus(0, "success"), ())))
  when(writeCheckpointMock.apply(WriteCheckpointArgs(partitioningId, checkpointV2DTO2)))
    .thenReturn(ZIO.left(DataConflictException(FunctionStatus(32, "Partitioning not found"))))
  when(writeCheckpointMock.apply(WriteCheckpointArgs(partitioningId, checkpointV2DTO3)))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val writeCheckpointV1MockLayer = ZLayer.succeed(writeCheckpointV1Mock)
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
          assertZIO(CheckpointRepository.writeCheckpoint(checkpointDTO2).exit)(
            failsWithA[ConflictDatabaseError]
          )
        },
        test("Returns expected DatabaseError") {
          assertZIO(CheckpointRepository.writeCheckpoint(checkpointDTO3).exit)(failsWithA[DatabaseError])
        }
      ),
      suite("WriteCheckpointV2Suite")(
        test("Returns an expected Unit") {
          for {
            result <- CheckpointRepository.writeCheckpointV2(partitioningId, checkpointV2DTO1)
          } yield assertTrue(result == ())
        },
        test("Fails with an expected ConflictDatabaseError") {
          assertZIO(CheckpointRepository.writeCheckpointV2(partitioningId, checkpointV2DTO2).exit)(
            failsWithA[ConflictDatabaseError]
          )
        },
        test("Fails with an expected GeneralDatabaseError") {
          assertZIO(CheckpointRepository.writeCheckpointV2(partitioningId, checkpointV2DTO3).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      )
    ).provide(CheckpointRepositoryImpl.layer, writeCheckpointV1MockLayer, writeCheckpointMockLayer)

  }

}

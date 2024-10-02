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
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningCheckpointV2.GetPartitioningCheckpointV2Args
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningCheckpoints.GetPartitioningCheckpointsArgs
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpointV2.WriteCheckpointArgs
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.atum.model.envelopes.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.db.fadb.exceptions.{DataConflictException, DataNotFoundException}
import za.co.absa.db.fadb.status.FunctionStatus
import zio._
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.test._
import za.co.absa.db.fadb.status.Row

object CheckpointRepositoryUnitTests extends ZIOSpecDefault with TestData {

  private val writeCheckpointMock: WriteCheckpoint = mock(classOf[WriteCheckpoint])
  private val getCheckpointMockV2: GetPartitioningCheckpointV2 = mock(classOf[GetPartitioningCheckpointV2])
  private val writeCheckpointV2Mock: WriteCheckpointV2 = mock(classOf[WriteCheckpointV2])
  private val getPartitioningCheckpointsMock: GetPartitioningCheckpoints = mock(classOf[GetPartitioningCheckpoints])

  when(writeCheckpointMock.apply(checkpointDTO1)).thenReturn(ZIO.right(Row(FunctionStatus(0, "success"), ())))
  when(writeCheckpointMock.apply(checkpointDTO2))
    .thenReturn(ZIO.fail(DataConflictException(FunctionStatus(31, "conflict"))))
  when(writeCheckpointMock.apply(checkpointDTO3)).thenReturn(ZIO.fail(new Exception("boom!")))

  private val partitioningId = 1L

  when(writeCheckpointV2Mock.apply(WriteCheckpointArgs(partitioningId, checkpointV2DTO1)))
    .thenReturn(ZIO.right(Row(FunctionStatus(0, "success"), ())))
  when(writeCheckpointV2Mock.apply(WriteCheckpointArgs(partitioningId, checkpointV2DTO2)))
    .thenReturn(ZIO.left(DataConflictException(FunctionStatus(32, "conflict"))))
  when(writeCheckpointV2Mock.apply(WriteCheckpointArgs(partitioningId, checkpointV2DTO3)))
    .thenReturn(ZIO.fail(new Exception("boom!")))
  when(writeCheckpointV2Mock.apply(WriteCheckpointArgs(0L, checkpointV2DTO3)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))

  when(getCheckpointMockV2.apply(GetPartitioningCheckpointV2Args(partitioningId, checkpointV2DTO1.id)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "OK"), Some(checkpointItemFromDB1)))))
  when(getCheckpointMockV2.apply(GetPartitioningCheckpointV2Args(partitioningId, checkpointV2DTO2.id)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getCheckpointMockV2.apply(GetPartitioningCheckpointV2Args(partitioningId, checkpointV2DTO3.id)))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  when(getPartitioningCheckpointsMock.apply(GetPartitioningCheckpointsArgs(0L, None, None, None)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "ok"), Some(checkpointItemFromDB1)))))
  when(getPartitioningCheckpointsMock.apply(GetPartitioningCheckpointsArgs(1L, None, None, None)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "ok"), None))))
  when(getPartitioningCheckpointsMock.apply(GetPartitioningCheckpointsArgs(3L, None, None, None)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))

  private val writeCheckpointMockLayer = ZLayer.succeed(writeCheckpointMock)
  private val getCheckpointV2MockLayer = ZLayer.succeed(getCheckpointMockV2)
  private val writeCheckpointV2MockLayer = ZLayer.succeed(writeCheckpointV2Mock)
  private val getPartitioningCheckpointsMockLayer = ZLayer.succeed(getPartitioningCheckpointsMock)

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
        },
        test("Fails with an expected NotFoundDatabaseError") {
          assertZIO(CheckpointRepository.writeCheckpointV2(0L, checkpointV2DTO3).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        }
      ),
      suite("GetCheckpointV2Suite")(
        test("Returns an expected Right with CheckpointV2DTO") {
          for {
            result <- CheckpointRepository.getCheckpointV2(partitioningId, checkpointV2DTO1.id)
          } yield assertTrue(result == checkpointV2DTO1)
        },
        test("Fails with an expected NotFoundDatabaseError") {
          assertZIO(CheckpointRepository.getCheckpointV2(partitioningId, checkpointV2DTO2.id).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns an expected DatabaseError") {
          assertZIO(CheckpointRepository.getCheckpointV2(partitioningId, checkpointV2DTO3.id).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetPartitioningCheckpointsSuite")(
        test("Returns expected Seq") {
          for {
            result <- CheckpointRepository.getPartitioningCheckpoints(0L, None, None, None)
          } yield assertTrue(
            result.isInstanceOf[ResultHasMore[CheckpointV2DTO]] && result.data == Seq(checkpointV2DTO1)
          )
        },
        test("Returns expected Seq.empty") {
          for {
            result <- CheckpointRepository.getPartitioningCheckpoints(1L, None, None, None)
          } yield assertTrue(
            result.isInstanceOf[ResultNoMore[CheckpointV2DTO]] && result.data == Seq.empty[CheckpointV2DTO]
          )
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(CheckpointRepository.getPartitioningCheckpoints(3L, None, None, None).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        }
      )
    ).provide(
      CheckpointRepositoryImpl.layer,
      writeCheckpointMockLayer,
      writeCheckpointV2MockLayer,
      getCheckpointV2MockLayer,
      getPartitioningCheckpointsMockLayer
    )

  }

}

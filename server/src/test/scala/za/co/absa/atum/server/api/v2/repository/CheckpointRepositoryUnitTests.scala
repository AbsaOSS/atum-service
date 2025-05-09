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

package za.co.absa.atum.server.api.v2.repository

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningCheckpointV2.GetPartitioningCheckpointV2Args
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningCheckpoints.GetPartitioningCheckpointsArgs
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpointV2.WriteCheckpointArgs
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.db.fadb.exceptions.{DataConflictException, DataNotFoundException}
import za.co.absa.db.fadb.status.{FunctionStatus, Row}
import zio._
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.test._

object CheckpointRepositoryUnitTests extends ZIOSpecDefault with TestData {

  private val getCheckpointMockV2: GetPartitioningCheckpointV2 = mock(classOf[GetPartitioningCheckpointV2])
  private val writeCheckpointV2Mock: WriteCheckpointV2 = mock(classOf[WriteCheckpointV2])
  private val getPartitioningCheckpointsMock: GetPartitioningCheckpoints = mock(classOf[GetPartitioningCheckpoints])

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

  private val getCheckpointV2MockLayer = ZLayer.succeed(getCheckpointMockV2)
  private val writeCheckpointV2MockLayer = ZLayer.succeed(writeCheckpointV2Mock)
  private val getPartitioningCheckpointsMockLayer = ZLayer.succeed(getPartitioningCheckpointsMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CheckpointRepositorySuite")(
      suite("WriteCheckpointV2Suite")(
        test("Returns an expected Unit") {
          for {
            result <- CheckpointRepository.writeCheckpoint(partitioningId, checkpointV2DTO1)
          } yield assertTrue(result == ())
        },
        test("Fails with an expected ConflictDatabaseError") {
          assertZIO(CheckpointRepository.writeCheckpoint(partitioningId, checkpointV2DTO2).exit)(
            failsWithA[ConflictDatabaseError]
          )
        },
        test("Fails with an expected GeneralDatabaseError") {
          assertZIO(CheckpointRepository.writeCheckpoint(partitioningId, checkpointV2DTO3).exit)(
            failsWithA[GeneralDatabaseError]
          )
        },
        test("Fails with an expected NotFoundDatabaseError") {
          assertZIO(CheckpointRepository.writeCheckpoint(0L, checkpointV2DTO3).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        }
      ),
      suite("GetCheckpointV2Suite")(
        test("Returns an expected Right with CheckpointV2DTO") {
          for {
            result <- CheckpointRepository.getCheckpoint(partitioningId, checkpointV2DTO1.id)
          } yield assertTrue(result == checkpointV2DTO1)
        },
        test("Fails with an expected NotFoundDatabaseError") {
          assertZIO(CheckpointRepository.getCheckpoint(partitioningId, checkpointV2DTO2.id).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns an expected DatabaseError") {
          assertZIO(CheckpointRepository.getCheckpoint(partitioningId, checkpointV2DTO3.id).exit)(
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
      writeCheckpointV2MockLayer,
      getCheckpointV2MockLayer,
      getPartitioningCheckpointsMockLayer
    )

  }

}

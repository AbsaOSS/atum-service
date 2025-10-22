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

package za.co.absa.atum.server.api.v2.service

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.v2.repository.CheckpointRepository
import za.co.absa.atum.server.model.PaginatedResult.ResultHasMore
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object CheckpointServiceUnitTests extends ZIOSpecDefault with TestData {

  private val checkpointRepositoryMock = mock(classOf[CheckpointRepository])

  private val partitioningId = 1L

  when(checkpointRepositoryMock.writeCheckpoint(partitioningId, checkpointV2DTO1)).thenReturn(ZIO.unit)
  when(checkpointRepositoryMock.writeCheckpoint(partitioningId, checkpointV2DTO2))
    .thenReturn(ZIO.fail(ConflictDatabaseError("conflict in data")))
  when(checkpointRepositoryMock.writeCheckpoint(partitioningId, checkpointV2DTO3))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))
  when(checkpointRepositoryMock.writeCheckpoint(0L, checkpointV2DTO3))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("Partitioning not found")))

  when(checkpointRepositoryMock.getCheckpoint(partitioningId, checkpointV2DTO1.id))
    .thenReturn(ZIO.succeed(checkpointV2DTO1))
  when(checkpointRepositoryMock.getCheckpoint(partitioningId, checkpointV2DTO2.id))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("not found")))

  when(checkpointRepositoryMock.getPartitioningCheckpoints(1L, 10, 0L, None))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(checkpointV2DTO1))))
    when(checkpointRepositoryMock.getPartitioningCheckpoints(0L, 10, 0L, None))
      .thenReturn(ZIO.fail(NotFoundDatabaseError("Partitioning not found")))

  private val checkpointRepositoryMockLayer = ZLayer.succeed(checkpointRepositoryMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CheckpointServiceSuite")(
      suite("SaveCheckpointV2Suite")(
        test("Returns an expected Unit") {
          for {
            result <- CheckpointService.saveCheckpoint(partitioningId, checkpointV2DTO1)
          } yield assertTrue(result == ())
        },
        test("Fails with an expected ConflictServiceError") {
          assertZIO(CheckpointService.saveCheckpoint(partitioningId, checkpointV2DTO2).exit)(
            failsWithA[ConflictServiceError]
          )
        },
        test("Fails with an expected GeneralServiceError") {
          assertZIO(CheckpointService.saveCheckpoint(partitioningId, checkpointV2DTO3).exit)(
            failsWithA[GeneralServiceError]
          )
        },
        test("Fails with an expected NotFoundServiceError") {
          assertZIO(CheckpointService.saveCheckpoint(0L, checkpointV2DTO3).exit)(
            failsWithA[NotFoundServiceError]
          )
        }
      ),
      suite("GetCheckpointV2Suite")(
        test("Returns an expected CheckpointV2DTO") {
          for {
            result <- CheckpointService.getCheckpoint(partitioningId, checkpointV2DTO1.id)
          } yield assertTrue(result == checkpointV2DTO1)
        },
        test("Fails with an expected NotFoundServiceError") {
          assertZIO(CheckpointService.getCheckpoint(partitioningId, checkpointV2DTO2.id).exit)(
            failsWithA[NotFoundServiceError]
          )
        }
      ),
      suite("GetPartitioningCheckpointsSuite")(
        test("Returns expected Right with Seq[CheckpointDTO]") {
          for {
            result <- CheckpointService.getPartitioningCheckpoints(1L, 10, 0L, None)
          } yield assertTrue {
            result == ResultHasMore(Seq(checkpointV2DTO1))
          }
        },
        test("Returns expected NotFoundServiceError") {
          assertZIO(CheckpointService.getPartitioningCheckpoints(0L, 10, 0L, None).exit)(
            failsWithA[NotFoundServiceError]
          )
        }
      )
    ).provide(
      CheckpointServiceImpl.layer,
      checkpointRepositoryMockLayer
    )

  }

}

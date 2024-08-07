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

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.repository.CheckpointRepository
import zio.test.Assertion.failsWithA
import zio.test._
import zio._

object CheckpointServiceUnitTests extends ZIOSpecDefault with TestData {

  private val checkpointRepositoryMock = mock(classOf[CheckpointRepository])

  when(checkpointRepositoryMock.writeCheckpoint(checkpointDTO1)).thenReturn(ZIO.unit)
  when(checkpointRepositoryMock.writeCheckpoint(checkpointDTO2))
    .thenReturn(ZIO.fail(GeneralDatabaseError("error in data")))
  when(checkpointRepositoryMock.writeCheckpoint(checkpointDTO3)).thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val partitioningId = 1L

  when(checkpointRepositoryMock.writeCheckpointV2(partitioningId, checkpointDTO1)).thenReturn(ZIO.unit)
  when(checkpointRepositoryMock.writeCheckpointV2(partitioningId, checkpointDTO2))
    .thenReturn(ZIO.fail(ConflictDatabaseError("conflict in data")))
  when(checkpointRepositoryMock.writeCheckpointV2(partitioningId, checkpointDTO3))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val checkpointRepositoryMockLayer = ZLayer.succeed(checkpointRepositoryMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CheckpointServiceSuite")(
      suite("SaveCheckpointSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- CheckpointService.saveCheckpoint(checkpointDTO1)
          } yield assertTrue(result == ())
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- CheckpointService.saveCheckpoint(checkpointDTO2).exit
          } yield assertTrue(
            result == Exit.fail(GeneralServiceError("Failed to perform 'saveCheckpoint': error in data"))
          )
        },
        test("Returns expected ServiceError") {
          assertZIO(CheckpointService.saveCheckpoint(checkpointDTO3).exit)(failsWithA[GeneralServiceError])
        }
      ),
      suite("SaveCheckpointV2Suite")(
        test("Returns an expected Unit") {
          for {
            result <- CheckpointService.saveCheckpointV2(partitioningId, checkpointDTO1)
          } yield assertTrue(result == ())
        },
        test("Fails with an expected ConflictServiceError") {
          assertZIO(CheckpointService.saveCheckpointV2(partitioningId, checkpointDTO2).exit)(
            failsWithA[ConflictServiceError]
          )
        },
        test("Fails with an expected GeneralServiceError") {
          assertZIO(CheckpointService.saveCheckpointV2(partitioningId, checkpointDTO3).exit)(
            failsWithA[GeneralServiceError]
          )
        }
      )
    ).provide(
      CheckpointServiceImpl.layer,
      checkpointRepositoryMockLayer
    )

  }

}

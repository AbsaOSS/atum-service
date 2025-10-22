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

package za.co.absa.atum.server.api.v2.controller

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.envelopes._
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.v2.service.CheckpointService
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object CheckpointControllerUnitTests extends ConfigProviderTest with TestData {

  private val checkpointServiceMock = mock(classOf[CheckpointService])

  private val partitioningId1 = 1L
  private val partitioningId2 = 2L

  when(checkpointServiceMock.saveCheckpoint(partitioningId1, checkpointV2DTO1)).thenReturn(ZIO.unit)
  when(checkpointServiceMock.saveCheckpoint(partitioningId1, checkpointV2DTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("error in data")))
  when(checkpointServiceMock.saveCheckpoint(partitioningId1, checkpointV2DTO3))
    .thenReturn(ZIO.fail(ConflictServiceError("boom!")))
  when(checkpointServiceMock.saveCheckpoint(0L, checkpointV2DTO3))
    .thenReturn(ZIO.fail(NotFoundServiceError("Partitioning not found")))

  when(checkpointServiceMock.getCheckpoint(partitioningId1, checkpointV2DTO1.id))
    .thenReturn(ZIO.succeed(checkpointV2DTO1))
  when(checkpointServiceMock.getCheckpoint(partitioningId1, checkpointV2DTO2.id))
    .thenReturn(ZIO.fail(NotFoundServiceError("not found")))
  when(checkpointServiceMock.getCheckpoint(partitioningId1, checkpointV2DTO3.id))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  when(checkpointServiceMock.getPartitioningCheckpoints(partitioningId1, 10, 0L, None))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(checkpointV2DTO1))))
  when(checkpointServiceMock.getPartitioningCheckpoints(partitioningId2, 10, 0L, None))
    .thenReturn(ZIO.succeed(ResultNoMore(Seq(checkpointV2DTO1))))
  when(checkpointServiceMock.getPartitioningCheckpoints(0L, 10, 0L, None))
    .thenReturn(ZIO.fail(NotFoundServiceError("Partitioning not found")))

  private val checkpointServiceMockLayer = ZLayer.succeed(checkpointServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CheckpointControllerIntegrationSuite")(
      suite("PostCheckpointV2Suite")(
        test("Returns expected CheckpointDTO") {
          for {
            result <- CheckpointController.postCheckpoint(partitioningId1, checkpointV2DTO1)
          } yield assertTrue(
            result._1.isInstanceOf[SingleSuccessResponse[CheckpointV2DTO]]
              && result._1.data == checkpointV2DTO1
              && result._2 == s"/api/v2/partitionings/$partitioningId1/checkpoints/${checkpointV2DTO1.id}"
          )
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(CheckpointController.postCheckpoint(1L, checkpointV2DTO2).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected ConflictServiceError") {
          assertZIO(CheckpointController.postCheckpoint(1L, checkpointV2DTO3).exit)(
            failsWithA[ConflictErrorResponse]
          )
        },
        test("Returns expected NotFoundServiceError") {
          assertZIO(CheckpointController.postCheckpoint(0L, checkpointV2DTO3).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        }
      ),
      suite("GetPartitioningCheckpointV2Suite")(
        test("Returns expected CheckpointDTO") {
          for {
            result <- CheckpointController.getPartitioningCheckpoint(partitioningId1, checkpointV2DTO1.id)
          } yield assertTrue(result.data == checkpointV2DTO1)
        },
        test("Returns expected NotFoundErrorResponse") {
          assertZIO(CheckpointController.getPartitioningCheckpoint(partitioningId1, checkpointV2DTO2.id).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(CheckpointController.getPartitioningCheckpoint(partitioningId1, checkpointV2DTO3.id).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
      suite("GetPartitioningCheckpointsSuite")(
        test("Returns expected Seq[CheckpointV2DTO] with Pagination indicating there is more data available") {
          for {
            result <- CheckpointController.getPartitioningCheckpoints(
              partitioningId1,
              limit = 10,
              offset = 0L
            )
          } yield assertTrue(
            result.data == Seq(checkpointV2DTO1) && result.pagination == Pagination(10, 0, hasMore = true)
          )
        },
        test("Returns expected Seq[CheckpointV2DTO] with Pagination indicating there is no more data available") {
          for {
            result <- CheckpointController.getPartitioningCheckpoints(
              partitioningId2,
              limit = 10,
              offset = 0L
            )
          } yield assertTrue(
            result.data == Seq(checkpointV2DTO1) && result.pagination == Pagination(10, 0, hasMore = false)
          )
        },
        test("Returns expected NotFoundErrorResponse when service returns NotFoundServiceError") {
          assertZIO(CheckpointController.getPartitioningCheckpoints(0L, 10, 0L).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        }
      )
    ).provide(
      CheckpointControllerImpl.layer,
      checkpointServiceMockLayer
    )

  }

}

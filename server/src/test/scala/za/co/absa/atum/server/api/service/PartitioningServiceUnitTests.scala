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
import za.co.absa.atum.server.api.repository.PartitioningRepository
import zio.test.Assertion.failsWithA
import zio.test._
import zio._

object PartitioningServiceUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningRepositoryMock = mock(classOf[PartitioningRepository])

  when(partitioningRepositoryMock.createPartitioningIfNotExists(partitioningSubmitDTO1)).thenReturn(ZIO.unit)
  when(partitioningRepositoryMock.createPartitioningIfNotExists(partitioningSubmitDTO2))
    .thenReturn(ZIO.fail(GeneralDatabaseError("error in data")))
  when(partitioningRepositoryMock.createPartitioningIfNotExists(partitioningSubmitDTO3))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.createOrUpdateAdditionalData(additionalDataSubmitDTO1)).thenReturn(ZIO.unit)
  when(partitioningRepositoryMock.createOrUpdateAdditionalData(additionalDataSubmitDTO2))
    .thenReturn(ZIO.fail(GeneralDatabaseError("error in AD data")))
  when(partitioningRepositoryMock.createOrUpdateAdditionalData(additionalDataSubmitDTO3))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioningMeasures(partitioningDTO1))
    .thenReturn(ZIO.succeed(Seq(measureDTO1, measureDTO2)))
  when(partitioningRepositoryMock.getPartitioningMeasures(partitioningDTO2))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioningAdditionalData(partitioningDTO1))
    .thenReturn(ZIO.succeed(initialAdditionalDataDTO1))
  when(partitioningRepositoryMock.getPartitioningAdditionalData(partitioningDTO2))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioningCheckpoints(checkpointQueryDTO1))
    .thenReturn(ZIO.succeed(Seq(checkpointFromDB1, checkpointFromDB2)))
  when(partitioningRepositoryMock.getPartitioningCheckpoints(checkpointQueryDTO2))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioningAdditionalDataV2(1L))
    .thenReturn(ZIO.succeed(additionalDataDTO1))
  when(partitioningRepositoryMock.getPartitioningAdditionalDataV2(2L))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioning(1111L)).thenReturn(ZIO.succeed(partitioningWithIdDTO1))
  when(partitioningRepositoryMock.getPartitioning(9999L))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))
  when(partitioningRepositoryMock.getPartitioning(8888L))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("Partitioning not found")))

  private val partitioningRepositoryMockLayer = ZLayer.succeed(partitioningRepositoryMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("PartitioningServiceSuite")(
      suite("CreatePartitioningIfNotExistsSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningService.createPartitioningIfNotExists(partitioningSubmitDTO1)
          } yield assertTrue(result == ())
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- PartitioningService.createPartitioningIfNotExists(partitioningSubmitDTO2).exit
          } yield assertTrue(
            result == Exit.fail(GeneralServiceError("Failed to perform 'createPartitioningIfNotExists': error in data"))
          )
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.createPartitioningIfNotExists(partitioningSubmitDTO3).exit)(
            failsWithA[ServiceError]
          )
        }
      ),
      suite("CreateOrUpdateAdditionalDataSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningService.createOrUpdateAdditionalData(additionalDataSubmitDTO1)
          } yield assertTrue(result == ())
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- PartitioningService.createOrUpdateAdditionalData(additionalDataSubmitDTO2).exit
          } yield assertTrue(
            result == Exit.fail(
              GeneralServiceError("Failed to perform 'createOrUpdateAdditionalData': error in AD data")
            )
          )
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.createOrUpdateAdditionalData(additionalDataSubmitDTO3).exit)(
            failsWithA[GeneralServiceError]
          )
        }
      ),
      suite("GetPartitioningMeasuresSuite")(
        test("Returns expected Right with Seq[MeasureDTO]") {
          for {
            result <- PartitioningService.getPartitioningMeasures(partitioningDTO1)
          } yield assertTrue {
            result == Seq(measureDTO1, measureDTO2)
          }
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningMeasures(partitioningDTO2).exit)(
            failsWithA[ServiceError]
          )
        }
      ),
      suite("GetPartitioningAdditionalDataSuite")(
        test("Returns expected Right with Seq[AdditionalDataDTO]") {
          for {
            result <- PartitioningService.getPartitioningAdditionalData(partitioningDTO1)
          } yield assertTrue { result == initialAdditionalDataDTO1 }
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningAdditionalData(partitioningDTO2).exit)(
            failsWithA[ServiceError]
          )
        }
      ),
      suite("GetPartitioningCheckpointsSuite")(
        test("Returns expected Right with Seq[CheckpointDTO]") {
          for {
            result <- PartitioningService.getPartitioningCheckpoints(checkpointQueryDTO1)
          } yield assertTrue {
            result == Seq(checkpointDTO1, checkpointDTO2.copy(partitioning = checkpointDTO1.partitioning))
          }
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningCheckpoints(checkpointQueryDTO2).exit)(
            failsWithA[ServiceError]
          )
        }
      ),
      suite("GetPartitioningAdditionalDataV2Suite")(
        test("Returns expected Right with AdditionalDataDTO") {
          for {
            result <- PartitioningService.getPartitioningAdditionalDataV2(1L)
          } yield assertTrue(result == additionalDataDTO1)
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningAdditionalDataV2(2L).exit)(
            failsWithA[ServiceError]
          )
        }
      ),
      suite("GetPartitioningByIDSuite")(
        test("Returns expected Right with PartitioningWithIdDTO") {
          for {
            result <- PartitioningService.getPartitioning(1111L)
          } yield assertTrue(result == partitioningWithIdDTO1)
        },
        test("Returns expected GeneralDatabaseError") {
          for {
            result <- PartitioningService.getPartitioning(9999L).exit
          } yield assertTrue(result == Exit.fail(GeneralServiceError("Failed to perform 'getPartitioning': boom!")))
        },
        test("Returns expected NotFoundDatabaseError") {
          for {
            result <- PartitioningService.getPartitioning(8888L).exit
          } yield assertTrue(
            result == Exit.fail(NotFoundServiceError("Failed to perform 'getPartitioning': Partitioning not found"))
          )
        }
      )
    ).provide(
      PartitioningServiceImpl.layer,
      partitioningRepositoryMockLayer
    )

  }
}

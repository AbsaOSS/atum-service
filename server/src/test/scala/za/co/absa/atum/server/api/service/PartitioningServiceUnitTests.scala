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
import za.co.absa.atum.model.dto.PartitioningWithIdDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.repository.PartitioningRepository
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
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

  when(partitioningRepositoryMock.createPartitioning(partitioningSubmitV2DTO1))
    .thenReturn(ZIO.succeed(PartitioningWithIdDTO(1L, Seq.empty, "author")))
  when(partitioningRepositoryMock.createPartitioning(partitioningSubmitV2DTO2))
    .thenReturn(ZIO.fail(ConflictDatabaseError("Partitioning already exists")))
  when(partitioningRepositoryMock.createPartitioning(partitioningSubmitV2DTO3))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.createOrUpdateAdditionalData(1L, additionalDataPatchDTO1))
    .thenReturn(ZIO.succeed(additionalDataDTO1))
  when(partitioningRepositoryMock.createOrUpdateAdditionalData(0L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("Partitioning not found")))
  when(partitioningRepositoryMock.createOrUpdateAdditionalData(2L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioningMeasures(partitioningDTO1))
    .thenReturn(ZIO.succeed(Seq(measureDTO1, measureDTO2)))
  when(partitioningRepositoryMock.getPartitioningMeasures(partitioningDTO2))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioningAdditionalData(1L))
    .thenReturn(ZIO.succeed(additionalDataDTO1))
  when(partitioningRepositoryMock.getPartitioningAdditionalData(2L))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioningById(1111L)).thenReturn(ZIO.succeed(partitioningWithIdDTO1))
  when(partitioningRepositoryMock.getPartitioningById(9999L))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))
  when(partitioningRepositoryMock.getPartitioningById(8888L))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("Partitioning not found")))

  when(partitioningRepositoryMock.getAncestors(1111L, Some(1), Some(1L)))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(partitioningWithIdDTO1))))
  when(partitioningRepositoryMock.getAncestors(2222L, Some(1), Some(1L)))
    .thenReturn(ZIO.succeed(ResultNoMore(Seq(partitioningWithIdDTO1))))
  when(partitioningRepositoryMock.getAncestors(8888L, Some(1), Some(1L)))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))
  when(partitioningRepositoryMock.getAncestors(9999L, Some(1), Some(1L)))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("Partitioning not found")))

  when(partitioningRepositoryMock.getPartitioningMeasuresById(1L))
    .thenReturn(ZIO.succeed(Seq(measureDTO1, measureDTO2)))
  when(partitioningRepositoryMock.getPartitioningMeasuresById(2L))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("boom!")))
  when(partitioningRepositoryMock.getPartitioningMeasuresById(3L))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioning(partitioningDTO1))
    .thenReturn(ZIO.succeed(partitioningWithIdDTO1))
  when(partitioningRepositoryMock.getPartitioning(partitioningDTO2))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("Partitioning not found")))
  when(partitioningRepositoryMock.getPartitioning(partitioningDTO3))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  when(partitioningRepositoryMock.getFlowPartitionings(1L, Some(1), Some(1L)))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(partitioningWithIdDTO1))))
  when(partitioningRepositoryMock.getFlowPartitionings(2L, Some(1), Some(1L)))
    .thenReturn(ZIO.succeed(ResultNoMore(Seq(partitioningWithIdDTO1))))
  when(partitioningRepositoryMock.getFlowPartitionings(3L, Some(1), Some(1L)))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))
  when(partitioningRepositoryMock.getFlowPartitionings(4L, Some(1), Some(1L)))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("Flow not found")))

  when(partitioningRepositoryMock.getPartitioningMainFlow(1L))
    .thenReturn(ZIO.succeed(flowDTO1))
  when(partitioningRepositoryMock.getPartitioningMainFlow(2L))
    .thenReturn(ZIO.fail(NotFoundDatabaseError("boom!")))
  when(partitioningRepositoryMock.getPartitioningMainFlow(3L))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

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
      suite("CreatePartitioningSuite")(
        test("Returns expected Right with PartitioningWithIdDTO") {
          for {
            result <- PartitioningService.createPartitioning(partitioningSubmitV2DTO1)
          } yield assertTrue(result == PartitioningWithIdDTO(1L, Seq.empty, "author"))
        },
        test("Returns expected ConflictServiceError") {
          for {
            result <- PartitioningService.createPartitioning(partitioningSubmitV2DTO2).exit
          } yield assertTrue(
            result == Exit.fail(
              ConflictServiceError("Failed to perform 'createPartitioning': Partitioning already exists")
            )
          )
        },
        test("Returns expected GeneralServiceError") {
          assertZIO(PartitioningService.createPartitioning(partitioningSubmitV2DTO3).exit)(
            failsWithA[GeneralServiceError]
          )
        }
      ),
      suite("PatchAdditionalDataSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningService.patchAdditionalData(1L, additionalDataPatchDTO1)
          } yield assertTrue(result == additionalDataDTO1)
        },
        test("Returns expected NotFoundServiceError") {
          for {
            result <- PartitioningService.patchAdditionalData(0L, additionalDataPatchDTO1).exit
          } yield assertTrue(
            result == Exit.fail(
              NotFoundServiceError("Failed to perform 'createOrUpdateAdditionalData': Partitioning not found")
            )
          )
        },
        test("Returns expected GeneralServiceError") {
          assertZIO(PartitioningService.patchAdditionalData(2L, additionalDataPatchDTO1).exit)(
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
        test("Returns expected Right with AdditionalDataDTO") {
          for {
            result <- PartitioningService.getPartitioningAdditionalData(1L)
          } yield assertTrue(result == additionalDataDTO1)
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningAdditionalData(2L).exit)(
            failsWithA[ServiceError]
          )
        }
      ),
      suite("GetPartitioningByIDSuite")(
        test("Returns expected Right with PartitioningWithIdDTO") {
          for {
            result <- PartitioningService.getPartitioningById(1111L)
          } yield assertTrue(result == partitioningWithIdDTO1)
        },
        test("Returns expected GeneralDatabaseError") {
          for {
            result <- PartitioningService.getPartitioningById(9999L).exit
          } yield assertTrue(result == Exit.fail(GeneralServiceError("Failed to perform 'getPartitioning': boom!")))
        },
        test("Returns expected NotFoundDatabaseError") {
          for {
            result <- PartitioningService.getPartitioningById(8888L).exit
          } yield assertTrue(
            result == Exit.fail(NotFoundServiceError("Failed to perform 'getPartitioning': Partitioning not found"))
          )
        }
      ),
      suite("GetAncestorsSuite")(
        test("Returns expected Right with ResultHasMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningService.getAncestors(1111L, Some(1), Some(1L))
          } yield assertTrue(result == ResultHasMore(Seq(partitioningWithIdDTO1)))
        },
        test("Returns expected Right with ResultNoMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningService.getAncestors(2222L, Some(1), Some(1L))
          } yield assertTrue(result == ResultNoMore(Seq(partitioningWithIdDTO1)))
        },
        test("Returns expected GeneralServiceError when database error occurs") {
          assertZIO(PartitioningService.getAncestors(8888L, Some(1), Some(1L)).exit)(
            failsWithA[GeneralServiceError]
          )
        },
        test("Returns expected NotFoundServiceError when child partition does not exist") {
          assertZIO(PartitioningService.getAncestors(9999L, Some(1), Some(1L)).exit)(
            failsWithA[NotFoundServiceError]
          )
        }
      ),
      suite("GetPartitioningMeasuresByIdSuite")(
        test("Returns expected Right with Seq[MeasureDTO]") {
          for {
            result <- PartitioningService.getPartitioningMeasuresById(1L)
          } yield assertTrue(result == Seq(measureDTO1, measureDTO2))
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningMeasuresById(2L).exit)(
            failsWithA[NotFoundServiceError]
          )
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningMeasuresById(3L).exit)(
            failsWithA[GeneralServiceError]
          )
        }
      ),
      suite("GetPartitioningSuite")(
        test("GetPartitioning - Returns expected Right with PartitioningWithIdDTO") {
          for {
            result <- PartitioningService.getPartitioning(partitioningDTO1)
          } yield assertTrue(result == partitioningWithIdDTO1)
        },
        test("GetPartitioning - Returns expected NotFoundServiceError") {
          for {
            result <- PartitioningService.getPartitioning(partitioningDTO2).exit
          } yield assertTrue(
            result == Exit.fail(NotFoundServiceError("Failed to perform 'getPartitioning': Partitioning not found"))
          )
        },
        test("GetPartitioning - Returns expected GeneralServiceError") {
          assertZIO(PartitioningService.getPartitioning(partitioningDTO3).exit)(
            failsWithA[GeneralServiceError]
          )
        }
      ),
      suite("GetPartitioningMainFlow")(
        test("Returns expected Right with FlowDTO") {
          for {
            result <- PartitioningService.getPartitioningMainFlow(1L)
          } yield assertTrue(result == flowDTO1)
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningMainFlow(2L).exit)(
            failsWithA[NotFoundServiceError]
          )
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningMainFlow(3L).exit)(
            failsWithA[GeneralServiceError]
          )
        }
      ),
      suite("GetFlowPartitioningsSuite")(
        test("Returns expected Right with ResultHasMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningService.getFlowPartitionings(1L, Some(1), Some(1L))
          } yield assertTrue(result == ResultHasMore(Seq(partitioningWithIdDTO1)))
        },
        test("Returns expected Right with ResultNoMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningService.getFlowPartitionings(2L, Some(1), Some(1L))
          } yield assertTrue(result == ResultNoMore(Seq(partitioningWithIdDTO1)))
        },
        test("Returns expected GeneralServiceError when database error occurs") {
          assertZIO(PartitioningService.getFlowPartitionings(3L, Some(1), Some(1L)).exit)(
            failsWithA[GeneralServiceError]
          )
        },
        test("Returns expected NotFoundServiceError when flow doesn't exist") {
          assertZIO(PartitioningService.getFlowPartitionings(4L, Some(1), Some(1L)).exit)(
            failsWithA[NotFoundServiceError]
          )
        }
      )
    )
  }.provide(
    PartitioningServiceImpl.layer,
    partitioningRepositoryMockLayer
  )
}

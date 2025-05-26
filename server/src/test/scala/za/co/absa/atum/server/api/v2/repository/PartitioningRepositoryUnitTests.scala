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
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataItemDTO, PartitioningWithIdDTO}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings.GetFlowPartitioningsArgs
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningAncestors
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningAncestors.GetPartitioningAncestorsArgs
import za.co.absa.atum.server.api.database.runs.functions.CreateOrUpdateAdditionalData.CreateOrUpdateAdditionalDataArgs
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.atum.server.model.database.{AdditionalDataItemFromDB, PartitioningForDB}
import za.co.absa.db.fadb.exceptions.{DataConflictException, DataNotFoundException}
import za.co.absa.db.fadb.status.{FunctionStatus, Row}
import zio._
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.test._

object PartitioningRepositoryUnitTests extends ZIOSpecDefault with TestData {

  // Create Partitioning If Not Exists V2 Mocks
  private val createPartitioningMock = mock(classOf[CreatePartitioning])

  when(createPartitioningMock.apply(partitioningSubmitV2DTO1))
    .thenReturn(ZIO.right(Row(FunctionStatus(11, "success"), partitioningWithIdDTO1.id)))
  when(createPartitioningMock.apply(partitioningSubmitV2DTO2))
    .thenReturn(ZIO.left(DataConflictException(FunctionStatus(31, "Partitioning already present"))))
  when(createPartitioningMock.apply(partitioningSubmitV2DTO3))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val createPartitioningMockLayer = ZLayer.succeed(createPartitioningMock)

  // Create Additional Data Mocks
  private val createOrUpdateAdditionalDataMock = mock(classOf[CreateOrUpdateAdditionalData])

  when(createOrUpdateAdditionalDataMock.apply(CreateOrUpdateAdditionalDataArgs(1L, additionalDataPatchDTO1)))
    .thenReturn(
      ZIO.right(
        Seq(
          Row(
            FunctionStatus(11, "Additional data have been updated, added or both"),
            Option.empty[AdditionalDataItemFromDB]
          )
        )
      )
    )
  when(createOrUpdateAdditionalDataMock.apply(CreateOrUpdateAdditionalDataArgs(0L, additionalDataPatchDTO1)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(createOrUpdateAdditionalDataMock.apply(CreateOrUpdateAdditionalDataArgs(2L, additionalDataPatchDTO1)))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val createOrUpdateAdditionalDataMockLayer = ZLayer.succeed(createOrUpdateAdditionalDataMock)

  // Get Partitioning Measures Mocks
  private val getPartitioningMeasuresMock = mock(classOf[GetPartitioningMeasures])

  when(getPartitioningMeasuresMock.apply(partitioningDTO1))
    .thenReturn(
      ZIO.right(
        Seq(Row(FunctionStatus(0, "success"), measureFromDB1), Row(FunctionStatus(0, "success"), measureFromDB2))
      )
    )
  when(getPartitioningMeasuresMock.apply(partitioningDTO2)).thenReturn(ZIO.fail(new Exception("boom!")))

  private val getPartitioningMeasuresMockLayer = ZLayer.succeed(getPartitioningMeasuresMock)

  // Get Partitioning By Id Mocks
  private val getPartitioningByIdByIdMock = mock(classOf[GetPartitioningById])

  when(getPartitioningByIdByIdMock.apply(1L))
    .thenReturn(ZIO.right(Row(FunctionStatus(11, "OK"), Some(partitioningFromDB1))))
  when(getPartitioningByIdByIdMock.apply(9999L))
    .thenReturn(ZIO.fail(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningByIdByIdMock.apply(8888L))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val getPartitioningByIdByIdMockLayer = ZLayer.succeed(getPartitioningByIdByIdMock)

  // GetPartitioningAdditionalData
  private val getPartitioningAdditionalDataMock = mock(classOf[GetPartitioningAdditionalData])

  when(getPartitioningAdditionalDataMock.apply(1L)).thenReturn(
    ZIO.right(Seq(Row(FunctionStatus(0, "success"), Some(AdditionalDataItemFromDB("key", Some("value"), "author")))))
  )
  when(getPartitioningAdditionalDataMock.apply(2L)).thenReturn(ZIO.fail(new Exception("boom!")))
  when(getPartitioningAdditionalDataMock.apply(3L)).thenReturn(
    ZIO.left(DataNotFoundException(FunctionStatus(41, "not found")))
  )

  private val getPartitioningAdditionalDataMockLayer = ZLayer.succeed(getPartitioningAdditionalDataMock)

  // GetPartitioningMeasuresById
  private val getPartitioningMeasuresV2Mock = mock(classOf[GetPartitioningMeasuresById])

  when(getPartitioningMeasuresV2Mock.apply(1L)).thenReturn(
    ZIO.right(Seq(Row(FunctionStatus(0, "success"), measureFromDB1), Row(FunctionStatus(0, "success"), measureFromDB2)))
  )
  when(getPartitioningMeasuresV2Mock.apply(2L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningMeasuresV2Mock.apply(3L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(42, "Measures not found"))))
  when(getPartitioningMeasuresV2Mock.apply(4L)).thenReturn(ZIO.fail(new Exception("boom!")))

  private val getPartitioningMeasuresV2MockLayer = ZLayer.succeed(getPartitioningMeasuresV2Mock)

  private val getPartitioningMock = mock(classOf[GetPartitioning])

  when(getPartitioningMock.apply(PartitioningForDB.fromSeqPartitionDTO(partitioningDTO1)))
    .thenReturn(ZIO.right(Row(FunctionStatus(11, "OK"), Some(partitioningFromDB1))))
  when(getPartitioningMock.apply(PartitioningForDB.fromSeqPartitionDTO(partitioningDTO2)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningMock.apply(PartitioningForDB.fromSeqPartitionDTO(partitioningDTO3)))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val getPartitioningMockLayer = ZLayer.succeed(getPartitioningMock)

  private val getFlowPartitioningsMock = mock(classOf[GetFlowPartitionings])

  when(getFlowPartitioningsMock.apply(GetFlowPartitioningsArgs(1L, Some(10), Some(0)))
    ).thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "OK"), Some(getFlowPartitioningsResult1)))))
  when(getFlowPartitioningsMock.apply(GetFlowPartitioningsArgs(2L, Some(10), Some(0)))
  ).thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "OK"), Some(getFlowPartitioningsResult2)))))
  when(getFlowPartitioningsMock.apply(GetFlowPartitioningsArgs(0L, None, None)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Flow not found"))))
  when(getFlowPartitioningsMock.apply(GetFlowPartitioningsArgs(3L, Some(10), Some(0)))
    ).thenReturn(ZIO.fail(new Exception("boom!")))

  private val getFlowPartitioningsMockLayer = ZLayer.succeed(getFlowPartitioningsMock)

  // Create Partitioning Mocks
  private val getPartitioningMainFlowMock = mock(classOf[GetPartitioningMainFlow])

  when(getPartitioningMainFlowMock.apply(1L)).thenReturn(
    ZIO.right(Row(FunctionStatus(0, "success"), Some(flowDTO1))))
  when(getPartitioningMainFlowMock.apply(2L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningMainFlowMock.apply(3L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(50, "Main flow not found"))))
  when(getPartitioningMainFlowMock.apply(4L)).thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val getPartitioningMainFlowMockLayer = ZLayer.succeed(getPartitioningMainFlowMock)

  // Get Ancestors By Id Mocks
  private val getPartitioningAncestorsMock = mock(classOf[GetPartitioningAncestors])

  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(1L, Some(10), Some(0)))
  ).thenReturn(ZIO.right(Seq(Row(FunctionStatus(10, "OK"), Some(getPartitioningAncestorsResult1)))))
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(1111L, Some(10), Some(0)))
  ).thenReturn(ZIO.right(Seq(Row(FunctionStatus(10, "OK"), Some(getPartitioningAncestorsResult2)))))
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(9999L, Some(10), Some(0)))
  ).thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(8888L, Some(10), Some(0)))
  ).thenReturn(ZIO.fail(new Exception("boom!")))

  private val getPartitioningAncestorsMockLayer = ZLayer.succeed(getPartitioningAncestorsMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("PartitioningRepositorySuite")(
      suite("CreatePartitioningSuite")(
        test("Returns expected Right with PartitioningWithIdDTO") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTO1)
          } yield assertTrue(result.isInstanceOf[PartitioningWithIdDTO])
        },
        test("Returns expected Left with DataConflictException") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTO2).exit
          } yield assertTrue(
            result == Exit.fail(
              ConflictDatabaseError(
                "Exception caused by operation: 'createPartitioning': (31) Partitioning already present"
              )
            )
          )
        },
        test("Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.createPartitioning(partitioningSubmitV2DTO3).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("CreateOrUpdateAdditionalDataSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningRepository.createOrUpdateAdditionalData(1L, additionalDataPatchDTO1)
          } yield assertTrue(result.isInstanceOf[AdditionalDataDTO])
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- PartitioningRepository.createOrUpdateAdditionalData(0L, additionalDataPatchDTO1).exit
          } yield assertTrue(
            result == Exit.fail(
              NotFoundDatabaseError(
                "Exception caused by operation: 'createOrUpdateAdditionalData': (41) Partitioning not found"
              )
            )
          )
        },
        test("Returns expected DatabaseError") {
          assertZIO(PartitioningRepository.createOrUpdateAdditionalData(2L, additionalDataPatchDTO1).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetPartitioningMeasuresSuite")(
        test("Returns expected Seq") {
          for {
            result <- PartitioningRepository.getPartitioningMeasures(partitioningDTO1)
          } yield assertTrue(result == Seq(measureDTO1, measureDTO2))
        },
        test("Returns expected Exception") {
          assertZIO(PartitioningRepository.getPartitioningMeasures(partitioningDTO2).exit)(
            failsWithA[DatabaseError]
          )
        }
      ),
      suite("GetPartitioningAdditionalDataSuite")(
        test("Returns expected AdditionalDataDTO instance") {
          for {
            result <- PartitioningRepository.getPartitioningAdditionalData(1L)
          } yield assertTrue(
            result == AdditionalDataDTO(Map.from(Seq("key" -> Some(AdditionalDataItemDTO("value", "author")))))
          )
        },
        test("Returns expected DatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningAdditionalData(2L).exit)(
            failsWithA[GeneralDatabaseError]
          )
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningAdditionalData(3L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        }
      ),
      suite("GetPartitioningByIdSuite")(
        test("GetPartitioningById - Returns expected PartitioningWithIdDTO") {
          for {
            result <- PartitioningRepository.getPartitioningById(1L)
          } yield assertTrue(result == partitioningWithIdDTO1)
        },
        test("GetPartitioningById - Returns expected DataNotFoundException") {
          for {
            result <- PartitioningRepository.getPartitioningById(9999L).exit
          } yield assertTrue(
            result == Exit.fail(
              NotFoundDatabaseError("Exception caused by operation: 'getPartitioningById': (41) Partitioning not found")
            )
          )
        },
        test("GetPartitioningById - Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningById(8888L).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetPartitioningMeasuresByIdSuite")(
        test("Returns expected Seq") {
          for {
            result <- PartitioningRepository.getPartitioningMeasuresById(1L)
          } yield assertTrue(result == Seq(measureDTO1, measureDTO2))
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningMeasuresById(2L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningMeasuresById(3L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningMeasuresById(4L).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetPartitioningMainFlowSuite")(
        test("Returns expected FlowDTO") {
          for {
            result <- PartitioningRepository.getPartitioningMainFlow(1L)
          } yield assertTrue(result == flowDTO1)
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningMainFlow(2L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningMainFlow(3L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningMainFlow(4L).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetFlowPartitioningsSuite")(
        test("Returns expected ResultNoMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningRepository.getFlowPartitionings(1L, Some(10), Some(0))
          } yield assertTrue(result == ResultNoMore(Seq(partitioningWithIdDTO1)))
        },
        test("Returns expected ResultHasMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningRepository.getFlowPartitionings(2L, Some(10), Some(0))
          } yield assertTrue(result == ResultHasMore(Seq(partitioningWithIdDTO2)))
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getFlowPartitionings(0L, None, None).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getFlowPartitionings(3L, Some(10), Some(0)).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetPartitioningSuite")(
        test("GetPartitioning - Returns expected PartitioningWithIdDTO") {
          for {
            result <- PartitioningRepository.getPartitioning(partitioningDTO1)
          } yield assertTrue(result == partitioningWithIdDTO1)
        },
        test("GetPartitioning - Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioning(partitioningDTO2).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("GetPartitioning - Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioning(partitioningDTO3).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetPartitioningAncestorsSuite")(
        test("Returns expected ResultNoMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningRepository.getPartitioningAncestors(1L, Some(10), Some(0))
          } yield assertTrue(result == ResultNoMore(Seq(partitioningWithIdDTO1)))
        },
        test("Returns expected ResultHasMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningRepository.getPartitioningAncestors(1111L, Some(10), Some(0))
          } yield assertTrue(result == ResultHasMore(Seq(partitioningWithIdDTO2)))
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningAncestors(9999L, Some(10), Some(0)).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningAncestors(8888L, Some(10), Some(0)).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      )
    )
  }.provide(
    PartitioningRepositoryImpl.layer,
    createPartitioningMockLayer,
    getPartitioningMeasuresMockLayer,
    createOrUpdateAdditionalDataMockLayer,
    getPartitioningByIdByIdMockLayer,
    getPartitioningAdditionalDataMockLayer,
    getPartitioningMockLayer,
    getPartitioningMeasuresV2MockLayer,
    getFlowPartitioningsMockLayer,
    getPartitioningMainFlowMockLayer,
    getPartitioningAncestorsMockLayer
  )

}

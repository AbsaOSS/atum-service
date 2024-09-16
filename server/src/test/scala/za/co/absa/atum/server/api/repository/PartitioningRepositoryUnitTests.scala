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
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataItemDTO}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.database.runs.functions.CreateOrUpdateAdditionalData.CreateOrUpdateAdditionalDataArgs
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.db.fadb.exceptions.{DataNotFoundException, ErrorInDataException}
import za.co.absa.db.fadb.status.{FunctionStatus, Row}
import zio._
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.test._
import za.co.absa.atum.server.model.{AdditionalDataFromDB, AdditionalDataItemFromDB}

object PartitioningRepositoryUnitTests extends ZIOSpecDefault with TestData {

  // Create Partitioning Mocks
  private val createPartitioningIfNotExistsMock = mock(classOf[CreatePartitioningIfNotExists])

  when(createPartitioningIfNotExistsMock.apply(partitioningSubmitDTO1))
    .thenReturn(ZIO.right(Row(FunctionStatus(0, "success"), ())))
  when(createPartitioningIfNotExistsMock.apply(partitioningSubmitDTO2))
    .thenReturn(ZIO.left(ErrorInDataException(FunctionStatus(50, "error in Partitioning data"))))
  when(createPartitioningIfNotExistsMock.apply(partitioningSubmitDTO3))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val createPartitioningIfNotExistsMockLayer = ZLayer.succeed(createPartitioningIfNotExistsMock)

  // Create Additional Data Mocks
  private val createOrUpdateAdditionalDataMock = mock(classOf[CreateOrUpdateAdditionalData])

  when(createOrUpdateAdditionalDataMock.apply(CreateOrUpdateAdditionalDataArgs(1L, additionalDataPatchDTO1)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "Additional data have been updated, added or both"), Option.empty[AdditionalDataItemFromDB]))))
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
  when(getPartitioningMeasuresMock.apply(partitioningDTO2)).thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val getPartitioningMeasuresMockLayer = ZLayer.succeed(getPartitioningMeasuresMock)

  // Get Partitioning Additional Data Mocks
  private val getPartitioningAdditionalDataMock = mock(classOf[GetPartitioningAdditionalData])

  when(getPartitioningAdditionalDataMock.apply(partitioningDTO1))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(0, "success"), AdditionalDataFromDB(Some("key"), Some("value"))))))
  when(getPartitioningAdditionalDataMock.apply(partitioningDTO2)).thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val getPartitioningAdditionalDataMockLayer = ZLayer.succeed(getPartitioningAdditionalDataMock)

  // Get Partitioning Checkpoints Mocks
  private val getPartitioningCheckpointsMock = mock(classOf[GetPartitioningCheckpoints])

  when(getPartitioningCheckpointsMock.apply(checkpointQueryDTO1))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(0, "success"), checkpointFromDB1))))
  when(getPartitioningCheckpointsMock.apply(checkpointQueryDTO3)).thenReturn(ZIO.right(Seq.empty))
  when(getPartitioningCheckpointsMock.apply(checkpointQueryDTO2)).thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val getPartitioningCheckpointsMockLayer = ZLayer.succeed(getPartitioningCheckpointsMock)

  // Get Partitioning By Id Mocks
  private val getPartitioningByIdMock = mock(classOf[GetPartitioningById])

  when(getPartitioningByIdMock.apply(1111L))
    .thenReturn(ZIO.right(Row(FunctionStatus(11, "OK"), Some(partitioningFromDB1))))
  when(getPartitioningByIdMock.apply(9999L))
    .thenReturn(ZIO.fail(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningByIdMock.apply(8888L))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val getPartitioningByIdMockLayer = ZLayer.succeed(getPartitioningByIdMock)

  // GetPartitioningAdditionalDataV2
  private val getPartitioningAdditionalDataV2Mock = mock(classOf[GetPartitioningAdditionalDataV2])

  when(getPartitioningAdditionalDataV2Mock.apply(1L)).thenReturn(
    ZIO.right(Seq(Row(FunctionStatus(0, "success"), Some(AdditionalDataItemFromDB("key", Some("value"), "author")))))
  )
  when(getPartitioningAdditionalDataV2Mock.apply(2L)).thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))
  when(getPartitioningAdditionalDataV2Mock.apply(3L)).thenReturn(
    ZIO.left(DataNotFoundException(FunctionStatus(41, "not found")))
  )

  private val getPartitioningAdditionalDataV2MockLayer = ZLayer.succeed(getPartitioningAdditionalDataV2Mock)

  // GetPartitioningMeasuresById
  private val getPartitioningMeasuresV2Mock = mock(classOf[GetPartitioningMeasuresById])

  when(getPartitioningMeasuresV2Mock.apply(1L)).thenReturn(
    ZIO.right(Seq(Row(FunctionStatus(0, "success"), measureFromDB1), Row(FunctionStatus(0, "success"), measureFromDB2))))
  when(getPartitioningMeasuresV2Mock.apply(2L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningMeasuresV2Mock.apply(3L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(42, "Measures not found"))))
  when(getPartitioningMeasuresV2Mock.apply(4L)).thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val getPartitioningMeasuresV2MockLayer = ZLayer.succeed(getPartitioningMeasuresV2Mock)

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

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("PartitioningRepositorySuite")(
      suite("CreatePartitioningIfNotExistsSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO1)
          } yield assertTrue(result == ())
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- PartitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO2).exit
          } yield assertTrue(
            result == Exit.fail(
              GeneralDatabaseError(
                "Exception caused by operation: 'createPartitioningIfNotExists': (50) error in Partitioning data"
              )
            )
          )
        },
        test("Returns expected DatabaseError") {
          assertZIO(PartitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO3).exit)(
            failsWithA[DatabaseError]
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
        test("Returns expected Right with Map") {
          for {
            result <- PartitioningRepository.getPartitioningAdditionalData(partitioningDTO1)
          } yield assertTrue(result.get("key").contains(Some("value")) && result.size == 1)
        },
        test("Returns expected Left with DatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningAdditionalData(partitioningDTO2).exit)(
            failsWithA[DatabaseError]
          )
        }
      ),
      suite("GetPartitioningCheckpointsSuite")(
        test("Returns expected Seq") {
          for {
            result <- PartitioningRepository.getPartitioningCheckpoints(checkpointQueryDTO1)
          } yield assertTrue(result == Seq(checkpointFromDB1))
        },
        test("Returns expected DatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningCheckpoints(checkpointQueryDTO2).exit)(
            failsWithA[DatabaseError]
          )
        },
        test("Returns expected Seq.empty") {
          for {
            result <- PartitioningRepository.getPartitioningCheckpoints(checkpointQueryDTO3)
          } yield assertTrue(result.isEmpty)
        }
      ),
      suite("GetPartitioningAdditionalDataV2Suite")(
        test("Returns expected AdditionalDataDTO instance") {
          for {
            result <- PartitioningRepository.getPartitioningAdditionalDataV2(1L)
          } yield assertTrue(
            result == AdditionalDataDTO(Map.from(Seq("key" -> Some(AdditionalDataItemDTO(Some("value"), "author")))))
          )
        },
        test("Returns expected DatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningAdditionalDataV2(2L).exit)(
            failsWithA[GeneralDatabaseError]
          )
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningAdditionalDataV2(3L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        }
      ),
      suite("GetPartitioningByIdSuite")(
        test("Returns expected PartitioningWithIdDTO") {
          for {
            result <- PartitioningRepository.getPartitioning(1111L)
          } yield assertTrue(result == partitioningWithIdDTO1)
        },
        test("Returns expected DataNotFoundException") {
          for {
            result <- PartitioningRepository.getPartitioning(9999L).exit
          } yield assertTrue(result == Exit.fail(NotFoundDatabaseError(
              "Exception caused by operation: 'getPartitioningById': (41) Partitioning not found"))
            )
        },
        test("Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioning(8888L).exit)(
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
      )
    ).provide(
      PartitioningRepositoryImpl.layer,
      createPartitioningIfNotExistsMockLayer,
      getPartitioningMeasuresMockLayer,
      getPartitioningAdditionalDataMockLayer,
      createOrUpdateAdditionalDataMockLayer,
      getPartitioningCheckpointsMockLayer,
      getPartitioningByIdMockLayer,
      getPartitioningAdditionalDataV2MockLayer,
      getPartitioningMeasuresV2MockLayer,
      getPartitioningMainFlowMockLayer
    )
  }

}

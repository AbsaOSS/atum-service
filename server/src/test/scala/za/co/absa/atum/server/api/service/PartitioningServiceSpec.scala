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

import org.junit.runner.RunWith
import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AtumContextDTO, MeasureDTO}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.{DatabaseError, ServiceError}
import za.co.absa.atum.server.api.repository.PartitioningRepository
import za.co.absa.fadb.exceptions.ErrorInDataException
import za.co.absa.fadb.status.FunctionStatus
import zio.test.Assertion.failsWithA
import zio.test._
import zio.test.junit.ZTestJUnitRunner
import zio._

@RunWith(classOf[ZTestJUnitRunner])
class PartitioningServiceSpec extends ZIOSpecDefault with TestData {

  private val partitioningRepositoryMock = mock(classOf[PartitioningRepository])

  when(partitioningRepositoryMock.createPartitioningIfNotExists(partitioningSubmitDTO1)).thenReturn(ZIO.right(()))
  when(partitioningRepositoryMock.createPartitioningIfNotExists(partitioningSubmitDTO2))
    .thenReturn(ZIO.left(ErrorInDataException(FunctionStatus(50, "error in data"))))
  when(partitioningRepositoryMock.createPartitioningIfNotExists(partitioningSubmitDTO3))
    .thenReturn(ZIO.fail(DatabaseError("boom!")))

  when(partitioningRepositoryMock.createOrUpdateAdditionalData(additionalDataSubmitDTO1)).thenReturn(ZIO.right(()))
  when(partitioningRepositoryMock.createOrUpdateAdditionalData(additionalDataSubmitDTO2))
    .thenReturn(ZIO.left(ErrorInDataException(FunctionStatus(50, "error in AD data"))))
  when(partitioningRepositoryMock.createOrUpdateAdditionalData(additionalDataSubmitDTO3))
    .thenReturn(ZIO.fail(DatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioningMeasures(partitioningSubmitDTO1))
    .thenReturn(ZIO.succeed(Seq.empty[MeasureDTO]))
  when(partitioningRepositoryMock.getPartitioningMeasures(partitioningSubmitDTO2))
    .thenReturn(ZIO.fail(DatabaseError("boom!")))
  when(partitioningRepositoryMock.getPartitioningMeasures(partitioningSubmitDTO3))
    .thenReturn(ZIO.fail(DatabaseError("boom!")))

  when(partitioningRepositoryMock.getPartitioningAdditionalData(partitioningSubmitDTO1))
    .thenReturn(ZIO.succeed(additionalDataDTO1))
  when(partitioningRepositoryMock.getPartitioningAdditionalData(partitioningSubmitDTO2))
    .thenReturn(ZIO.fail(DatabaseError("boom!")))
  when(partitioningRepositoryMock.getPartitioningAdditionalData(partitioningSubmitDTO3))
    .thenReturn(ZIO.fail(DatabaseError("boom!")))


  private val partitioningRepositoryMockLayer = ZLayer.succeed(partitioningRepositoryMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("PartitioningServiceSuite")(
      suite("CreatePartitioningIfNotExistsSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningService.createPartitioningIfNotExists(partitioningSubmitDTO1)
          } yield assertTrue(result.isRight)
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- PartitioningService.createPartitioningIfNotExists(partitioningSubmitDTO2)
          } yield assertTrue(result.isLeft)
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
          } yield assertTrue(result.isRight)
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- PartitioningService.createOrUpdateAdditionalData(additionalDataSubmitDTO2)
          } yield assertTrue(result.isLeft)
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.createOrUpdateAdditionalData(additionalDataSubmitDTO3).exit)(
            failsWithA[ServiceError]
          )
        }
      ),
      suite("GetPartitioningMeasuresSuite")(
        test("Returns expected Right with Seq[MeasureDTO]") {
          for {
            result <- PartitioningService.getPartitioningMeasures(partitioningSubmitDTO1)
          } yield assertTrue(result.isInstanceOf[Seq[MeasureDTO]])
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningMeasures(partitioningSubmitDTO2).exit)(
            failsWithA[ServiceError]
          )
        }
      ),
      suite("GetPartitioningAdditionalDataSuite")(
        test("Returns expected Right with Seq[AdditionalDataDTO]") {
          for {
            result <- PartitioningService.getPartitioningAdditionalData(partitioningSubmitDTO1)
          } yield assertTrue(result.isInstanceOf[AdditionalDataDTO])
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.getPartitioningAdditionalData(partitioningSubmitDTO2).exit)(
            failsWithA[ServiceError]
          )
        }
      ),
      suite("ReturnAtumContextSuite")(
        test("Returns expected AtumContextDTO") {
          for {
            result <- PartitioningService.returnAtumContext(partitioningSubmitDTO1)
          } yield {
            print("Results: ", result.partitioning)
            assertTrue(result.isInstanceOf[AtumContextDTO])
            assertTrue(result.partitioning == partitioningSubmitDTO1.partitioning)
          }
        },
        test("Returns expected ServiceError") {
          assertZIO(PartitioningService.returnAtumContext(partitioningSubmitDTO2).exit)(
            failsWithA[ServiceError]
          )
        }
      )
    ).provide(
      PartitioningServiceImpl.layer,
      partitioningRepositoryMockLayer
    )

  }
}

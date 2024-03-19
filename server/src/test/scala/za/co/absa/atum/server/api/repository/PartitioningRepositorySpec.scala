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

import org.junit.runner.RunWith
import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.database.runs.functions.{CreateOrUpdateAdditionalData, CreatePartitioningIfNotExists}
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.TestData
import za.co.absa.fadb.exceptions.ErrorInDataException
import za.co.absa.fadb.status.FunctionStatus
import zio._
import zio.test.Assertion.failsWithA
import zio.test._
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class PartitioningRepositorySpec extends ZIOSpecDefault with TestData {

  // Create Partitioning Mocks
  private val createPartitioningIfNotExistsMock = mock(classOf[CreatePartitioningIfNotExists])

  when(createPartitioningIfNotExistsMock.apply(partitioningSubmitDTO1)).thenReturn(ZIO.right(()))
  when(createPartitioningIfNotExistsMock.apply(partitioningSubmitDTO2))
    .thenReturn(ZIO.left(ErrorInDataException(FunctionStatus(50, "error in Partitioning data"))))
  when(createPartitioningIfNotExistsMock.apply(partitioningSubmitDTO3))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val createPartitioningIfNotExistsMockLayer = ZLayer.succeed(createPartitioningIfNotExistsMock)

  // Create Additional Data Mocks
  private val createOrUpdateAdditionalDataMock = mock(classOf[CreateOrUpdateAdditionalData])

  when(createOrUpdateAdditionalDataMock.apply(additionalDataSubmitDTO1)).thenReturn(ZIO.right(()))
  when(createOrUpdateAdditionalDataMock.apply(additionalDataSubmitDTO2))
    .thenReturn(ZIO.left(ErrorInDataException(FunctionStatus(50, "error in AD data"))))
  when(createOrUpdateAdditionalDataMock.apply(additionalDataSubmitDTO3))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val createOrUpdateAdditionalDataMockLayer = ZLayer.succeed(createOrUpdateAdditionalDataMock)


  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("PartitioningRepositorySuite")(
      suite("CreatePartitioningIfNotExistsSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO1)
          } yield assertTrue(result.isRight)
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- PartitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO2)
          } yield assertTrue(result.isLeft)
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
            result <- PartitioningRepository.createOrUpdateAdditionalData(additionalDataSubmitDTO1)
          } yield assertTrue(result.isRight)
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- PartitioningRepository.createOrUpdateAdditionalData(additionalDataSubmitDTO2)
          } yield assertTrue(result.isLeft)
        },
        test("Returns expected DatabaseError") {
          assertZIO(PartitioningRepository.createOrUpdateAdditionalData(additionalDataSubmitDTO3).exit)(
            failsWithA[DatabaseError]
          )
        }
      )
    ).provide(
      PartitioningRepositoryImpl.layer,
      createPartitioningIfNotExistsMockLayer,
      createOrUpdateAdditionalDataMockLayer
    )

  }

}

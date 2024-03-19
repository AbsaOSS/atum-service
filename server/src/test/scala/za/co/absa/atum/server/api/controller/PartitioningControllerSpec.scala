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

package za.co.absa.atum.server.api.controller

import org.junit.runner.RunWith
import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AtumContextDTO, MeasureDTO}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.service.PartitioningService
import za.co.absa.atum.server.model.{GeneralErrorResponse, InternalServerErrorResponse}
import za.co.absa.fadb.exceptions.ErrorInDataException
import za.co.absa.fadb.status.FunctionStatus
import zio.test.Assertion.failsWithA
import zio._
import zio.test._
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class PartitioningControllerSpec extends ZIOSpecDefault with TestData {

  private val partitioningServiceMock = mock(classOf[PartitioningService])

  when(partitioningServiceMock.createPartitioningIfNotExists(partitioningSubmitDTO1)).thenReturn(ZIO.right(()))
  when(partitioningServiceMock.createPartitioningIfNotExists(partitioningSubmitDTO2))
    .thenReturn(ZIO.left(ErrorInDataException(FunctionStatus(50, "error in data"))))
  when(partitioningServiceMock.createPartitioningIfNotExists(partitioningSubmitDTO3))
    .thenReturn(ZIO.fail(ServiceError("boom!")))

  private val partitioningServiceMockLayer = ZLayer.succeed(partitioningServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("PartitioningControllerSuite")(
      suite("CreatePartitioningIfNotExistsSuite")(
        test("Returns expected AtumContextDTO") {
          val expectedAtumContextDTO = AtumContextDTO(
            partitioning = partitioningSubmitDTO1.partitioning,
            measures = Set(MeasureDTO("count", Seq("*"))),
            additionalData = Map.empty
          )
          for {
            result <- PartitioningController.createPartitioningIfNotExists(partitioningSubmitDTO1)
          } yield assertTrue(result == expectedAtumContextDTO)
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.createPartitioningIfNotExists(partitioningSubmitDTO3).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected GeneralErrorResponse") {
          assertZIO(PartitioningController.createPartitioningIfNotExists(partitioningSubmitDTO2).exit)(
            failsWithA[GeneralErrorResponse]
          )
        }
      )
    ).provide(
      PartitioningControllerImpl.layer,
      partitioningServiceMockLayer
    )

  }

}

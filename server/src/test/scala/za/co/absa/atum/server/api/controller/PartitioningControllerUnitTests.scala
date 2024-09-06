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

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError.{GeneralServiceError, NotFoundServiceError}
import za.co.absa.atum.server.api.service.PartitioningService
import za.co.absa.atum.server.model.{InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object PartitioningControllerUnitTests extends ZIOSpecDefault with TestData {
  private val partitioningServiceMock = mock(classOf[PartitioningService])

  when(partitioningServiceMock.createPartitioningIfNotExists(partitioningSubmitDTO1))
    .thenReturn(ZIO.unit)
  when(partitioningServiceMock.createPartitioningIfNotExists(partitioningSubmitDTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  when(partitioningServiceMock.getPartitioningMeasures(partitioningDTO1))
    .thenReturn(ZIO.succeed(Seq(measureDTO1, measureDTO2)))

  when(partitioningServiceMock.getPartitioningAdditionalData(partitioningDTO1))
    .thenReturn(ZIO.succeed(Map.empty))

  when(partitioningServiceMock.patchAdditionalData(1L, additionalDataPatchDTO1))
    .thenReturn(ZIO.succeed(additionalDataDTO1))
  when(partitioningServiceMock.patchAdditionalData(0L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(NotFoundServiceError("Partitioning not found")))
  when(partitioningServiceMock.patchAdditionalData(2L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  when(partitioningServiceMock.getPartitioningAdditionalDataV2(1L))
    .thenReturn(ZIO.succeed(additionalDataDTO1))
  when(partitioningServiceMock.getPartitioningAdditionalDataV2(2L))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(partitioningServiceMock.getPartitioningAdditionalDataV2(3L))
    .thenReturn(ZIO.fail(NotFoundServiceError("not found")))

  when(partitioningServiceMock.getPartitioning(11L))
    .thenReturn(ZIO.succeed(partitioningWithIdDTO1))
  when(partitioningServiceMock.getPartitioning(22L))
    .thenReturn(ZIO.fail(NotFoundServiceError("not found")))
  when(partitioningServiceMock.getPartitioning(99L))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  private val partitioningServiceMockLayer = ZLayer.succeed(partitioningServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("PartitioningControllerSuite")(
      suite("CreatePartitioningIfNotExistsSuite")(
        test("Returns expected AtumContextDTO") {
          for {
            result <- PartitioningController.createPartitioningIfNotExistsV1(partitioningSubmitDTO1)
          } yield assertTrue(result == atumContextDTO1)
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.createPartitioningIfNotExistsV1(partitioningSubmitDTO2).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
      suite("PatchAdditionalDataSuite")(
        test("Returns expected AdditionalDataSubmitDTO") {
          for {
            result <- PartitioningController.patchPartitioningAdditionalDataV2(1L, additionalDataPatchDTO1)
            expected = SingleSuccessResponse(additionalDataDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected NotFoundErrorResponse") {
          assertZIO(PartitioningController.patchPartitioningAdditionalDataV2(0L, additionalDataPatchDTO1).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.patchPartitioningAdditionalDataV2(2L, additionalDataPatchDTO1).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
      suite("GetPartitioningSuite")(
        test("Returns expected PartitioningWithIdDTO") {
          for {
            result <- PartitioningController.getPartitioningV2(11L)
            expected = SingleSuccessResponse(partitioningWithIdDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected NotFoundErrorResponse") {
          assertZIO(PartitioningController.getPartitioningV2(22L).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.getPartitioningV2(99L).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
      suite("GetPartitioningAdditionalDataV2Suite")(
        test("Returns expected AdditionalDataDTO") {
          for {
            result <- PartitioningController.getPartitioningAdditionalDataV2(1L)
            expected = SingleSuccessResponse(additionalDataDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.getPartitioningAdditionalDataV2(2L).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected NotFoundServiceError") {
          assertZIO(PartitioningController.getPartitioningAdditionalDataV2(3L).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        }
      )
    ).provide(
      PartitioningControllerImpl.layer,
      partitioningServiceMockLayer
    )
  }
}

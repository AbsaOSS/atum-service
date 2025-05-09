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
import za.co.absa.atum.model.envelopes.{ConflictErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse, Pagination}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.service.PartitioningService
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.atum.model.envelopes.SuccessResponse.{PaginatedResponse, SingleSuccessResponse}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object PartitioningControllerUnitTests extends ZIOSpecDefault with TestData {
  private val partitioningServiceMock = mock(classOf[PartitioningService])

  when(partitioningServiceMock.createPartitioningIfNotExists(partitioningSubmitDTO1))
    .thenReturn(ZIO.unit)
  when(partitioningServiceMock.createPartitioningIfNotExists(partitioningSubmitDTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTO1))
    .thenReturn(ZIO.succeed(partitioningWithIdDTO1))
  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTO3))
    .thenReturn(ZIO.fail(ConflictServiceError("Partitioning already present")))

  when(partitioningServiceMock.getPartitioningMeasures(partitioningDTO1))
    .thenReturn(ZIO.succeed(Seq(measureDTO1, measureDTO2)))

  when(partitioningServiceMock.patchAdditionalData(1L, additionalDataPatchDTO1))
    .thenReturn(ZIO.succeed(additionalDataDTO1))
  when(partitioningServiceMock.patchAdditionalData(0L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(NotFoundServiceError("Partitioning not found")))
  when(partitioningServiceMock.patchAdditionalData(2L, additionalDataPatchDTO1))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  when(partitioningServiceMock.getPartitioningAdditionalData(1L))
    .thenReturn(ZIO.succeed(additionalDataDTO1))
  when(partitioningServiceMock.getPartitioningAdditionalData(2L))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(partitioningServiceMock.getPartitioningAdditionalData(3L))
    .thenReturn(ZIO.fail(NotFoundServiceError("not found")))

  when(partitioningServiceMock.getPartitioningById(11L))
    .thenReturn(ZIO.succeed(partitioningWithIdDTO1))
  when(partitioningServiceMock.getPartitioningById(22L))
    .thenReturn(ZIO.fail(NotFoundServiceError("not found")))
  when(partitioningServiceMock.getPartitioningById(99L))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  when(partitioningServiceMock.getPartitioning(partitioningDTO1))
    .thenReturn(ZIO.succeed(partitioningWithIdDTO1))
  when(partitioningServiceMock.getPartitioning(partitioningDTO2))
    .thenReturn(ZIO.fail(NotFoundServiceError("Partitioning not found")))
  when(partitioningServiceMock.getPartitioning(partitioningDTO3))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  when(partitioningServiceMock.getFlowPartitionings(1L, Some(1), Some(0)))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(partitioningWithIdDTO1))))
  when(partitioningServiceMock.getFlowPartitionings(2L, Some(1), Some(0)))
    .thenReturn(ZIO.succeed(ResultNoMore(Seq(partitioningWithIdDTO1))))
  when(partitioningServiceMock.getFlowPartitionings(3L, Some(1), Some(0)))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(partitioningServiceMock.getFlowPartitionings(4L, Some(1), Some(0)))
    .thenReturn(ZIO.fail(NotFoundServiceError("Flow not found")))

  when(partitioningServiceMock.getPartitioningMainFlow(111L))
    .thenReturn(ZIO.succeed(flowDTO1))
  when(partitioningServiceMock.getPartitioningMainFlow(222L))
    .thenReturn(ZIO.fail(NotFoundServiceError("not found")))
  when(partitioningServiceMock.getPartitioningMainFlow(999L))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  private val partitioningId1 = 1L

  when(partitioningServiceMock.patchPartitioningParent(partitioningId1, partitioningParentPatchDTO1))
    .thenReturn(ZIO.unit)
  when(partitioningServiceMock.patchPartitioningParent(partitioningId1, partitioningParentPatchDTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("error in data")))
  when(partitioningServiceMock.patchPartitioningParent(partitioningId1, partitioningParentPatchDTO3))
    .thenReturn(ZIO.fail(ConflictServiceError("boom!")))
  when(partitioningServiceMock.patchPartitioningParent(1L, partitioningParentPatchDTO5))
    .thenReturn(ZIO.fail(NotFoundServiceError("Parent Partitioning not found")))
  when(partitioningServiceMock.patchPartitioningParent(0L, partitioningParentPatchDTO3))
    .thenReturn(ZIO.fail(NotFoundServiceError("Child Partitioning not found")))

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
      suite("CreatePartitioningSuite")(
        test("Returns expected PartitioningWithIdDTO") {
          for {
            result <- PartitioningController.postPartitioning(partitioningSubmitV2DTO1)
            expectedData = SingleSuccessResponse(partitioningWithIdDTO1, uuid1)
            actualData = result._1.copy(requestId = uuid1)
            expectedUri = s"/api/v2/partitionings/${partitioningWithIdDTO1.id}"
            actualUri = result._2
          } yield assertTrue(actualData == expectedData && actualUri == expectedUri)
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.postPartitioning(partitioningSubmitV2DTO2).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected ConflictServiceError") {
          assertZIO(PartitioningController.postPartitioning(partitioningSubmitV2DTO3).exit)(
            failsWithA[ConflictErrorResponse]
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
      suite("PatchPartitioningParentSuite")(
        test("Returns expected PartitioningParentPatchDTO") {
          for {
            result <- PartitioningController.patchPartitioningParent(partitioningId1, partitioningParentPatchDTO1)
            expected = SingleSuccessResponse(partitioningParentPatchDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.patchPartitioningParent(partitioningId1, partitioningParentPatchDTO2).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected ConflictServiceError") {
          assertZIO(PartitioningController.patchPartitioningParent(partitioningId1, partitioningParentPatchDTO3).exit)(
            failsWithA[ConflictErrorResponse]
          )
        },
        test("Returns expected NotFoundErrorResponse") {
          assertZIO(PartitioningController.patchPartitioningParent(1L, partitioningParentPatchDTO5).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Returns expected NotFoundErrorResponse") {
          assertZIO(PartitioningController.patchPartitioningParent(0L, partitioningParentPatchDTO3).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        }
      ),
      suite("GetPartitioningByIdSuite")(
        test("Returns expected PartitioningWithIdDTO") {
          for {
            result <- PartitioningController.getPartitioningByIdV2(11L)
            expected = SingleSuccessResponse(partitioningWithIdDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected NotFoundErrorResponse") {
          assertZIO(PartitioningController.getPartitioningByIdV2(22L).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.getPartitioningByIdV2(99L).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
      suite("GetPartitioningAdditionalDataV2Suite")(
        test("Returns expected AdditionalDataDTO") {
          for {
            result <- PartitioningController.getPartitioningAdditionalData(1L)
            expected = SingleSuccessResponse(additionalDataDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.getPartitioningAdditionalData(2L).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected NotFoundServiceError") {
          assertZIO(PartitioningController.getPartitioningAdditionalData(3L).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        }
      ),
      suite("GetFlowPartitioningsSuite")(
        test("Returns expected PaginatedResponse[PartitioningWithIdDTO] with more data available") {
          for {
            result <- PartitioningController.getFlowPartitionings(1L, Some(1), Some(0))
            expected = PaginatedResponse(Seq(partitioningWithIdDTO1), Pagination(1, 0L, hasMore = true), uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected PaginatedResponse[PartitioningWithIdDTO] with no more data available") {
          for {
            result <- PartitioningController.getFlowPartitionings(2L, Some(1), Some(0))
            expected = PaginatedResponse(Seq(partitioningWithIdDTO1), Pagination(1, 0L, hasMore = false), uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected InternalServerErrorResponse when service call fails with GeneralServiceError") {
          assertZIO(PartitioningController.getFlowPartitionings(3L, Some(1), Some(0)).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected NotFoundErrorResponse when service call fails with NotFoundServiceError") {
          assertZIO(PartitioningController.getFlowPartitionings(4L, Some(1), Some(0)).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        }
      ),
      suite("GetPartitioningSuite")(
        test("GetPartitioning - Returns expected PartitioningWithIdDTO") {
          for {
            result <- PartitioningController.getPartitioning(partitioningDTO1.asBase64EncodedJsonString)
            expected = SingleSuccessResponse(partitioningWithIdDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("GetPartitioning - Returns expected NotFoundErrorResponse") {
          assertZIO(PartitioningController.getPartitioning(partitioningDTO2.asBase64EncodedJsonString).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("GetPartitioning - Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.getPartitioning(partitioningDTO3.asBase64EncodedJsonString).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
      suite("GetPartitioningMainFlowSuite")(
        test("Returns expected FlowDTO") {
          for {
            result <- PartitioningController.getPartitioningMainFlow(111L)
            expected = SingleSuccessResponse(flowDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected NotFoundErrorResponse") {
          assertZIO(PartitioningController.getPartitioningMainFlow(222L).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.getPartitioningMainFlow(999L).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      )
    ).provide(
      PartitioningControllerImpl.layer,
      partitioningServiceMockLayer
    )
  }
}

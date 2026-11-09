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

import org.mockito.Mockito.{mock, never, times, verify, when}
import sttp.model.StatusCode
import za.co.absa.atum.model.dto.ParentPartitioningDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.{MultiSuccessResponse, PaginatedResponse, SingleSuccessResponse}
import za.co.absa.atum.model.envelopes._
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.v2.service.PartitioningService
import za.co.absa.atum.server.model.CreationResult.{Created, Existing}
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object PartitioningControllerUnitTests extends ZIOSpecDefault with TestData {
  private val partitioningServiceMock = mock(classOf[PartitioningService])

  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTO1))
    .thenReturn(ZIO.succeed(Created(partitioningWithIdDTO1)))
  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTO3))
    .thenReturn(ZIO.fail(ConflictServiceError("Partitioning parent link would create a cycle")))

  private val dto100 = partitioningSubmitV2DTO1.copy(parentPartitioningIds = 1L to 100L, author = "p100")
  private val dto101 = partitioningSubmitV2DTO1.copy(parentPartitioningIds = 1L to 101L, author = "p101")
  private val dtoDupHeavy =
    partitioningSubmitV2DTO1.copy(parentPartitioningIds = (1L to 50L) ++ (1L to 50L) ++ (1L to 50L), author = "pdup")

  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTOMultiParent))
    .thenReturn(ZIO.succeed(Created(partitioningWithIdDTO1)))
  private val dupDeduped = partitioningSubmitV2DTOMultiParentDup.copy(parentPartitioningIds = Seq(1L, 2L))
  when(partitioningServiceMock.createPartitioning(dupDeduped))
    .thenReturn(ZIO.succeed(Created(partitioningWithIdDTO1)))
  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTOExistingAuthor))
    .thenReturn(ZIO.succeed(Existing(partitioningWithIdDTO2.copy(id = 77L, author = "stored-author"))))
  when(partitioningServiceMock.createPartitioning(dto100))
    .thenReturn(ZIO.succeed(Created(partitioningWithIdDTO1)))
  when(partitioningServiceMock.createPartitioning(dtoDupHeavy.copy(parentPartitioningIds = 1L to 50L)))
    .thenReturn(ZIO.succeed(Created(partitioningWithIdDTO1)))
  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTOMultiParentNotFound))
    .thenReturn(ZIO.fail(NotFoundServiceError("parent not found")))
  when(partitioningServiceMock.createPartitioning(partitioningSubmitV2DTOMultiParentCycle))
    .thenReturn(ZIO.fail(ConflictServiceError("cycle")))

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

  when(partitioningServiceMock.getFlowPartitionings(1L, 1, 0L))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(partitioningWithIdDTO1))))
  when(partitioningServiceMock.getFlowPartitionings(2L,  1, 0L))
    .thenReturn(ZIO.succeed(ResultNoMore(Seq(partitioningWithIdDTO1))))
  when(partitioningServiceMock.getFlowPartitionings(3L,  1, 0L))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(partitioningServiceMock.getFlowPartitionings(4L,  1, 0L))
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

  when(partitioningServiceMock.getPartitioningAncestors(1111L, 1, 0L))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(partitioningWithIdDTO1))))
  when(partitioningServiceMock.getPartitioningAncestors(2222L, 1, 0L))
    .thenReturn(ZIO.succeed(ResultNoMore(Seq(partitioningWithIdDTO1))))
  when(partitioningServiceMock.getPartitioningAncestors(5555L, 10, 0L))
    .thenReturn(ZIO.succeed(ResultNoMore(Seq(partitioningWithIdDTO1, partitioningWithIdDTO2))))
  when(partitioningServiceMock.getPartitioningAncestors(6666L, 2, 0L))
    .thenReturn(ZIO.succeed(ResultHasMore(Seq(partitioningWithIdDTO1, partitioningWithIdDTO2))))
  when(partitioningServiceMock.getPartitioningAncestors(8888L, 1, 0L))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(partitioningServiceMock.getPartitioningAncestors(9999L, 1, 0L))
    .thenReturn(ZIO.fail(NotFoundServiceError("Partitioning not found")))

  when(partitioningServiceMock.getPartitioningParents(1111L))
    .thenReturn(ZIO.succeed(Seq.empty[ParentPartitioningDTO]))
  when(partitioningServiceMock.getPartitioningParents(5555L))
    .thenReturn(ZIO.succeed(Seq(parentPartitioningDTO1, parentPartitioningDTO2)))
  when(partitioningServiceMock.getPartitioningParents(8888L))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(partitioningServiceMock.getPartitioningParents(9999L))
    .thenReturn(ZIO.fail(NotFoundServiceError("Partitioning not found")))

  private val partitioningServiceMockLayer = ZLayer.succeed(partitioningServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("PartitioningControllerSuite")(
      suite("CreatePartitioningSuite")(
        test("Created -> 201 with Location and the single-success body") {
          for {
            result <- PartitioningController.postPartitioning(partitioningSubmitV2DTO1)
            expectedData = SingleSuccessResponse(partitioningWithIdDTO1, uuid1)
            actualData = result._2.copy(requestId = uuid1)
            expectedUri = s"/api/v2/partitionings/${partitioningWithIdDTO1.id}"
            actualUri = result._3
          } yield assertTrue(result._1 == StatusCode.Created && actualData == expectedData && actualUri == expectedUri)
        },
        test("Existing -> 200 with Location and the same body shape (stored author)") {
          for {
            result <- PartitioningController.postPartitioning(partitioningSubmitV2DTOExistingAuthor)
            expectedBody = partitioningWithIdDTO2.copy(id = 77L, author = "stored-author")
          } yield assertTrue(
            result._1 == StatusCode.Ok,
            result._2.copy(requestId = uuid1) == SingleSuccessResponse(expectedBody, uuid1),
            result._2.data.author == "stored-author",
            result._3 == "/api/v2/partitionings/77"
          )
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
        },
        test("Multi-parent succeeds with 201-style response and Location uri") {
          for {
            result <- PartitioningController.postPartitioning(partitioningSubmitV2DTOMultiParent)
          } yield assertTrue(
            result._1 == StatusCode.Created &&
              result._2.copy(requestId = uuid1) == SingleSuccessResponse(partitioningWithIdDTO1, uuid1) &&
              result._3 == s"/api/v2/partitionings/${partitioningWithIdDTO1.id}"
          )
        },
        test("100 distinct parents is accepted") {
          assertZIO(PartitioningController.postPartitioning(dto100).map(_._2.data))(
            Assertion.equalTo(partitioningWithIdDTO1)
          )
        },
        test("101 distinct parents fails with BadRequestResponse and does not call the service") {
          for {
            exit <- PartitioningController.postPartitioning(dto101).exit
          } yield assertTrue(exit.causeOption.flatMap(_.failureOption).exists(_.isInstanceOf[BadRequestResponse])) &&
            assertTrue({ verify(partitioningServiceMock, never()).createPartitioning(dto101); true })
        },
        test("150 ids deduplicating to 50 are accepted and the service gets the deduped DTO") {
          val deduped = dtoDupHeavy.copy(parentPartitioningIds = 1L to 50L)
          for {
            data <- PartitioningController.postPartitioning(dtoDupHeavy).map(_._2.data)
          } yield assertTrue(
            data == partitioningWithIdDTO1,
            { verify(partitioningServiceMock, times(1)).createPartitioning(deduped); true },
            { verify(partitioningServiceMock, never()).createPartitioning(dtoDupHeavy); true }
          )
        },
        test("duplicate ids are removed before the service call, order preserved") {
          val deduped = dupDeduped
          for {
            result <- PartitioningController.postPartitioning(partitioningSubmitV2DTOMultiParentDup)
          } yield assertTrue(
            result._1 == StatusCode.Created,
            { verify(partitioningServiceMock, times(1)).createPartitioning(deduped); true },
            { verify(partitioningServiceMock, never()).createPartitioning(partitioningSubmitV2DTOMultiParentDup); true }
          )
        },
        test("Multi-parent NotFoundServiceError maps to NotFoundErrorResponse") {
          assertZIO(PartitioningController.postPartitioning(partitioningSubmitV2DTOMultiParentNotFound).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Multi-parent cycle ConflictServiceError maps to ConflictErrorResponse") {
          assertZIO(PartitioningController.postPartitioning(partitioningSubmitV2DTOMultiParentCycle).exit)(
            failsWithA[ConflictErrorResponse]
          )
        }
      ),
      suite("PatchAdditionalDataSuite")(
        test("Returns expected AdditionalDataSubmitDTO") {
          for {
            result <- PartitioningController.patchPartitioningAdditionalData(1L, additionalDataPatchDTO1)
            expected = MultiSuccessResponse(additionalDataDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected NotFoundErrorResponse") {
          assertZIO(PartitioningController.patchPartitioningAdditionalData(0L, additionalDataPatchDTO1).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.patchPartitioningAdditionalData(2L, additionalDataPatchDTO1).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
      suite("PatchPartitioningParentSuite")(
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
            result <- PartitioningController.getPartitioningById(11L)
            expected = SingleSuccessResponse(partitioningWithIdDTO1, uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected NotFoundErrorResponse") {
          assertZIO(PartitioningController.getPartitioningById(22L).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.getPartitioningById(99L).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
      suite("GetPartitioningAdditionalDataV2Suite")(
        test("Returns expected AdditionalDataDTO") {
          for {
            result <- PartitioningController.getPartitioningAdditionalData(1L)
            expected = MultiSuccessResponse(additionalDataDTO1, uuid1)
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
            result <- PartitioningController.getFlowPartitionings(1L,  1, 0L)
            expected = PaginatedResponse(Seq(partitioningWithIdDTO1), Pagination(1, 0L, hasMore = true), uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected PaginatedResponse[PartitioningWithIdDTO] with no more data available") {
          for {
            result <- PartitioningController.getFlowPartitionings(2L,  1, 0L)
            expected = PaginatedResponse(Seq(partitioningWithIdDTO1), Pagination(1, 0L, hasMore = false), uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected InternalServerErrorResponse when service call fails with GeneralServiceError") {
          assertZIO(PartitioningController.getFlowPartitionings(3L,  1, 0L).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected NotFoundErrorResponse when service call fails with NotFoundServiceError") {
          assertZIO(PartitioningController.getFlowPartitionings(4L,  1, 0L).exit)(
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
      ),
      suite("GetPartitioningAncestorsSuite")(
        test("Returns expected PaginatedResponse[PartitioningWithIdDTO] with more data available") {
          for {
            result <- PartitioningController.getPartitioningAncestors(1111L, 1, 0L)
            expected = PaginatedResponse(Seq(partitioningWithIdDTO1), Pagination(1, 0L, hasMore = true), uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected PaginatedResponse[PartitioningWithIdDTO] with no more data available") {
          for {
            result <- PartitioningController.getPartitioningAncestors(2222L, 1, 0L)
            expected = PaginatedResponse(Seq(partitioningWithIdDTO1), Pagination(1, 0L, hasMore = false), uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns all ancestors of a multi-parent child in a PaginatedResponse with no more data") {
          for {
            result <- PartitioningController.getPartitioningAncestors(5555L, 10, 0L)
            expected = PaginatedResponse(
              Seq(partitioningWithIdDTO1, partitioningWithIdDTO2),
              Pagination(10, 0L, hasMore = false),
              uuid1
            )
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(
            actual == expected,
            actual.data.map(_.id).distinct.size == actual.data.size
          )
        },
        test("Returns all ancestors of a multi-parent child in a PaginatedResponse with more data") {
          for {
            result <- PartitioningController.getPartitioningAncestors(6666L, 2, 0L)
            expected = PaginatedResponse(
              Seq(partitioningWithIdDTO1, partitioningWithIdDTO2),
              Pagination(2, 0L, hasMore = true),
              uuid1
            )
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected InternalServerErrorResponse when service call fails with GeneralServiceError") {
          assertZIO(PartitioningController.getPartitioningAncestors(8888L, 1, 0L).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected NotFoundErrorResponse when service call fails with NotFoundServiceError") {
          assertZIO(PartitioningController.getPartitioningAncestors(9999L, 1, 0L).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        }
      ),
      suite("GetPartitioningParentsSuite")(
        test("Returns expected empty MultiSuccessResponse when partitioning has no parents") {
          for {
            result <- PartitioningController.getPartitioningParents(1111L)
            expected = MultiSuccessResponse(Seq.empty[ParentPartitioningDTO], uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns all parents in MultiSuccessResponse") {
          for {
            result <- PartitioningController.getPartitioningParents(5555L)
            expected = MultiSuccessResponse(Seq(parentPartitioningDTO1, parentPartitioningDTO2), uuid1)
            actual = result.copy(requestId = uuid1)
          } yield assertTrue(actual == expected)
        },
        test("Returns expected InternalServerErrorResponse when service call fails with GeneralServiceError") {
          assertZIO(PartitioningController.getPartitioningParents(8888L).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected NotFoundErrorResponse when service call fails with NotFoundServiceError") {
          assertZIO(PartitioningController.getPartitioningParents(9999L).exit)(
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

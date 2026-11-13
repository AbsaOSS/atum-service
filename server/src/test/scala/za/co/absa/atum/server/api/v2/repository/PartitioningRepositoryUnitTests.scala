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

import org.mockito.Mockito.{mock, mockingDetails, times, verify, when}
import za.co.absa.atum.model.dto.{
  AdditionalDataItemV2DTO,
  PartitioningParentPatchDTO,
  PartitioningSubmitV2DTO,
  PartitioningWithIdDTO
}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings.GetFlowPartitioningsArgs
import za.co.absa.atum.server.api.database.lineage.functions.{GetDirectChildren, GetDirectParents, GetPartitioningAncestors, GetPartitioningDescendants, GetPartitioningLineageGraph}
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningLineageGraph.GetPartitioningLineageGraphArgs
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningAncestors.GetPartitioningAncestorsArgs
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningDescendants.{
  GetPartitioningDescendantsArgs,
  GetPartitioningDescendantsResult
}
import za.co.absa.atum.server.api.database.runs.functions.CreateOrUpdateAdditionalData.CreateOrUpdateAdditionalDataArgs
import za.co.absa.atum.server.api.database.runs.functions.UpdatePartitioningParent.UpdatePartitioningParentArgs
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.atum.server.model.CreationResult.{Created, Existing}
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.atum.server.model.database.{AdditionalDataItemFromDB, PartitioningForDB}
import za.co.absa.db.fadb.exceptions.{DataConflictException, DataNotFoundException}
import za.co.absa.db.fadb.status.{FunctionStatus, Row}
import zio._
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.test._

import scala.jdk.CollectionConverters._

object PartitioningRepositoryUnitTests extends ZIOSpecDefault with TestData {

  // Create Partitioning With Parents Mocks (the single DB path of PartitioningRepository.createPartitioning)
  private val createPartitioningWithParentsMock = mock(classOf[CreatePartitioningWithParents])

  // The DB function returns the id and the STORED created_by. Every stub below returns a stored author that
  // differs from the requester's author, so a "use the request author" regression fails the tests (SERVER-04).
  private val storedAuthor = "stored-author"
  private val createdId = partitioningWithIdDTO1.id

  private def createdRow(code: Int, text: String, createdBy: String) =
    ZIO.right(
      Row(
        FunctionStatus(code, text),
        CreatePartitioningWithParents.DBResult(createdId, createdBy, code == 11 || code == 12)
      )
    )

  private def expected(createdBy: String): PartitioningWithIdDTO =
    PartitioningWithIdDTO(createdId, partitioningDTO1, createdBy)

  private def timesCalled(dto: PartitioningSubmitV2DTO, count: Int) =
    ZIO.attempt(verify(createPartitioningWithParentsMock, times(count)).apply(dto))

  /** DTOs the mocked DB function was really invoked with (by author), read from Mockito's invocation record. */
  private def receivedByCreateFn(author: String): Seq[PartitioningSubmitV2DTO] =
    mockingDetails(createPartitioningWithParentsMock).getInvocations.asScala.toSeq
      .map(_.getArgument[PartitioningSubmitV2DTO](0))
      .filter(_.author == author)

  // 0 / 1 / N parents all go to the SAME mock
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTO1))
    .thenReturn(createdRow(11, "success", "stored-0"))
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTOSingleParent))
    .thenReturn(createdRow(12, "ok", "stored-1"))
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTOMultiParent))
    .thenReturn(createdRow(12, "ok", "stored-n"))
  // ids reach the DB function untouched (no dedupe/normalisation in the repository)
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTODuplicateIds))
    .thenReturn(createdRow(12, "ok", "stored-dup"))

  private val dto11 = partitioningSubmitV2DTOMultiParent.copy(author = "req11")
  private val dto12 = partitioningSubmitV2DTOMultiParent.copy(author = "req12")
  private val dto13 = partitioningSubmitV2DTOExistingAuthor.copy(author = "req13")
  private val dto14 = partitioningSubmitV2DTOExistingAuthor.copy(author = "req14")

  when(createPartitioningWithParentsMock.apply(dto11)).thenReturn(createdRow(11, "created", storedAuthor))
  when(createPartitioningWithParentsMock.apply(dto12)).thenReturn(createdRow(12, "created + linked", storedAuthor))
  when(createPartitioningWithParentsMock.apply(dto13)).thenReturn(createdRow(13, "new parent", storedAuthor))
  when(createPartitioningWithParentsMock.apply(dto14)).thenReturn(createdRow(14, "nothing written", storedAuthor))
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTOExistingAuthor))
    .thenReturn(createdRow(14, "nothing written", storedAuthor))

  // The "already exists" is a success (13/14), a conflict is a cycle (32) or descendants (33); the old
  // single-parent status 31 "Partitioning already present" no longer exists
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTO2))
    .thenReturn(ZIO.left(DataConflictException(FunctionStatus(32, "Cycle detected"))))
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTO3))
    .thenReturn(ZIO.fail(new Exception("boom!")))
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTOMultiParentNotFound))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Parent partitioning not found"))))
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTOMultiParentCycle))
    .thenReturn(ZIO.left(DataConflictException(FunctionStatus(32, "Cycle detected"))))
  when(createPartitioningWithParentsMock.apply(partitioningSubmitV2DTOMultiParentBoom))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val createPartitioningWithParentsMockLayer = ZLayer.succeed(createPartitioningWithParentsMock)

  // Create Additional Data Mocks
  private val createOrUpdateAdditionalDataMock = mock(classOf[CreateOrUpdateAdditionalData])

  when(createOrUpdateAdditionalDataMock.apply(CreateOrUpdateAdditionalDataArgs(1L, additionalDataPatchDTO1)))
    .thenReturn(
      ZIO.right(Seq.empty)
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
    ZIO.right(Seq(Row(FunctionStatus(0, "success"), AdditionalDataItemFromDB("key", Some("value"), "author"))))
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

  when(getFlowPartitioningsMock.apply(GetFlowPartitioningsArgs(1L, 10, 0L)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "OK"), Some(getFlowPartitioningsResult1)))))
  when(getFlowPartitioningsMock.apply(GetFlowPartitioningsArgs(2L, 10, 0L)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "OK"), Some(getFlowPartitioningsResult2)))))
  when(getFlowPartitioningsMock.apply(GetFlowPartitioningsArgs(0L, 10, 0L)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Flow not found"))))
  when(getFlowPartitioningsMock.apply(GetFlowPartitioningsArgs(3L, 10, 0L)))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val getFlowPartitioningsMockLayer = ZLayer.succeed(getFlowPartitioningsMock)

  // Create Partitioning Mocks
  private val getPartitioningMainFlowMock = mock(classOf[GetPartitioningMainFlow])

  when(getPartitioningMainFlowMock.apply(1L)).thenReturn(ZIO.right(Row(FunctionStatus(0, "success"), Some(flowDTO1))))
  when(getPartitioningMainFlowMock.apply(2L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningMainFlowMock.apply(3L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(50, "Main flow not found"))))
  when(getPartitioningMainFlowMock.apply(4L)).thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val getPartitioningMainFlowMockLayer = ZLayer.succeed(getPartitioningMainFlowMock)

  // Update Parent Partitioning Mocks
  private val updatePartitioningParentMock = mock(classOf[UpdatePartitioningParent])

  when(updatePartitioningParentMock.apply(UpdatePartitioningParentArgs(1L, partitioningParentPatchDTO1)))
    .thenReturn(ZIO.right(Row(FunctionStatus(11, "OK"), ())))
  when(updatePartitioningParentMock.apply(UpdatePartitioningParentArgs(1L, partitioningParentPatchDTO5)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(42, "Parent Partitioning not found"))))
  when(updatePartitioningParentMock.apply(UpdatePartitioningParentArgs(0L, partitioningParentPatchDTO1)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Child Partitioning not found"))))
  when(updatePartitioningParentMock.apply(UpdatePartitioningParentArgs(3L, partitioningParentPatchDTO1)))
    .thenReturn(ZIO.fail(GeneralDatabaseError("boom!")))

  private val cyclePatchDTO = PartitioningParentPatchDTO(parentPartitioningId = 5L, author = "cycle-author")
  when(updatePartitioningParentMock.apply(UpdatePartitioningParentArgs(5L, cyclePatchDTO)))
    .thenReturn(
      ZIO.left(DataConflictException(FunctionStatus(32, "Parent partitioning would create a cycle: 5")))
    )

  private val descendantsPatchDTO = PartitioningParentPatchDTO(parentPartitioningId = 6L, author = "desc-author")
  when(updatePartitioningParentMock.apply(UpdatePartitioningParentArgs(6L, descendantsPatchDTO)))
    .thenReturn(
      ZIO.left(
        DataConflictException(
          FunctionStatus(33, "Partitioning already has descendants and cannot be linked under new parents: 6")
        )
      )
    )

  private val updatePartitioningParentMockLayer = ZLayer.succeed(updatePartitioningParentMock)

  private def isPatchWarning(partitioningId: Long)(entry: ZTestLogger.LogEntry): Boolean =
    entry.logLevel == LogLevel.Warning &&
      entry.message().contains(s"PATCH /ancestors rejected for partitioning $partitioningId ")

  // Get Ancestors By Id Mocks
  private val getPartitioningAncestorsMock = mock(classOf[GetPartitioningAncestors])

  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(1L, 10, 0L)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(10, "OK"), Some(getPartitioningAncestorsResult1)))))
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(1111L, 10, 0L)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(10, "OK"), Some(getPartitioningAncestorsResult2)))))
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(3333L, 10, 0L))).thenReturn(
    ZIO.right(
      Seq(
        Row(FunctionStatus(10, "OK"), Some(getPartitioningAncestorsResult1)),
        Row(FunctionStatus(10, "OK"), Some(getPartitioningAncestorsResult2NoMore))
      )
    )
  )
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(4444L, 10, 0L))).thenReturn(
    ZIO.right(
      Seq(
        Row(FunctionStatus(10, "OK"), Some(getPartitioningAncestorsResult1HasMore)),
        Row(FunctionStatus(10, "OK"), Some(getPartitioningAncestorsResult2))
      )
    )
  )
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(9999L, 10, 0L)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(8888L, 10, 0L)))
    .thenReturn(ZIO.fail(new Exception("boom!")))
  // existing partitioning WITHOUT ancestors: the wrapper yields a status-14 row without data (see
  // GetPartitioningAncestorsIntegrationTests, which pins that through the real wrapper), or no rows at all
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(5555L, 10, 0L)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(14, "OK"), None))))
  when(getPartitioningAncestorsMock.apply(GetPartitioningAncestorsArgs(6666L, 10, 0L))).thenReturn(ZIO.right(Seq.empty))

  // Get Descendants Mocks
  private val getPartitioningDescendantsMock = mock(classOf[GetPartitioningDescendants])

  private val descendantResult =
    GetPartitioningDescendantsResult(1L, getPartitioningAncestorsResult1.partitioningJson, "author", hasMore = false)

  when(getPartitioningDescendantsMock.apply(GetPartitioningDescendantsArgs(1L, 10, 0L)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(10, "OK"), Some(descendantResult)))))
  when(getPartitioningDescendantsMock.apply(GetPartitioningDescendantsArgs(1111L, 10, 0L)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(10, "OK"), Some(descendantResult.copy(hasMore = true))))))
  when(getPartitioningDescendantsMock.apply(GetPartitioningDescendantsArgs(5555L, 10, 0L)))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(14, "OK"), None))))
  when(getPartitioningDescendantsMock.apply(GetPartitioningDescendantsArgs(9999L, 10, 0L)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))

  private val getPartitioningDescendantsMockLayer = ZLayer.succeed(getPartitioningDescendantsMock)

  private val getPartitioningAncestorsMockLayer = ZLayer.succeed(getPartitioningAncestorsMock)

  // Get Direct Parents Mocks
  private val getDirectParentsMock = mock(classOf[GetDirectParents])

  when(getDirectParentsMock.apply(1L))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "OK"), Some(getDirectParentsResult1)))))
  when(getDirectParentsMock.apply(3333L)).thenReturn(
    ZIO.right(
      Seq(
        Row(FunctionStatus(11, "OK"), Some(getDirectParentsResult1)),
        Row(FunctionStatus(11, "OK"), Some(getDirectParentsResult2))
      )
    )
  )
  when(getDirectParentsMock.apply(6666L)).thenReturn(ZIO.right(Seq.empty))
  when(getDirectParentsMock.apply(9999L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getDirectParentsMock.apply(8888L))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val getDirectParentsMockLayer = ZLayer.succeed(getDirectParentsMock)

  // Get Direct Children Mocks
  private val getDirectChildrenMock = mock(classOf[GetDirectChildren])

  when(getDirectChildrenMock.apply(1L))
    .thenReturn(ZIO.right(Seq(Row(FunctionStatus(11, "OK"), Some(getDirectChildrenResult1)))))
  when(getDirectChildrenMock.apply(3333L)).thenReturn(
    ZIO.right(
      Seq(
        Row(FunctionStatus(11, "OK"), Some(getDirectChildrenResult1)),
        Row(FunctionStatus(11, "OK"), Some(getDirectChildrenResult2))
      )
    )
  )
  when(getDirectChildrenMock.apply(6666L)).thenReturn(ZIO.right(Seq.empty))
  when(getDirectChildrenMock.apply(9999L))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getDirectChildrenMock.apply(8888L))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val getDirectChildrenMockLayer = ZLayer.succeed(getDirectChildrenMock)

  // Get Partitioning Lineage Graph Mocks
  private val getPartitioningLineageGraphMock = mock(classOf[GetPartitioningLineageGraph])

  when(getPartitioningLineageGraphMock.apply(GetPartitioningLineageGraphArgs(2L, "both", 5)))
    .thenReturn(ZIO.right(Row(FunctionStatus(11, "OK"), Some(lineageGraphResult1))))
  when(getPartitioningLineageGraphMock.apply(GetPartitioningLineageGraphArgs(9999L, "both", 5)))
    .thenReturn(ZIO.left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
  when(getPartitioningLineageGraphMock.apply(GetPartitioningLineageGraphArgs(8888L, "both", 5)))
    .thenReturn(ZIO.fail(new Exception("boom!")))

  private val getPartitioningLineageGraphMockLayer = ZLayer.succeed(getPartitioningLineageGraphMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("PartitioningRepositorySuite")(
      suite("CreatePartitioningSuite")(
        test("0 parents, status 11 -> Created with the stored author, one call on the single DB function") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTO1)
            _ <- timesCalled(partitioningSubmitV2DTO1, 1)
          } yield assertTrue(
            result == Created(expected("stored-0")),
            result.data.author != partitioningSubmitV2DTO1.author
          )
        },
        test("1 parent, status 12 -> Created, same single DB function (times(1))") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTOSingleParent)
            _ <- timesCalled(partitioningSubmitV2DTOSingleParent, 1)
          } yield assertTrue(result == Created(expected("stored-1")))
        },
        test("N parents, status 12 -> Created, same single DB function (times(1))") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTOMultiParent)
            _ <- timesCalled(partitioningSubmitV2DTOMultiParent, 1)
          } yield assertTrue(result == Created(expected("stored-n")))
        },
        test("duplicate parent ids reach the DB function unchanged (no dedupe in the repository)") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTODuplicateIds)
            _ <- timesCalled(partitioningSubmitV2DTODuplicateIds, 1)
            received <- ZIO.succeed(receivedByCreateFn(partitioningSubmitV2DTODuplicateIds.author))
          } yield assertTrue(
            // what the DB function actually received, independently of the fixture constant
            received.map(_.parentPartitioningIds) == Seq(Seq(1L, 1L, 2L)),
            result == Created(expected("stored-dup"))
          )
        },
        test("statuses 11 and 12 -> Created") {
          for {
            r11 <- PartitioningRepository.createPartitioning(dto11)
            r12 <- PartitioningRepository.createPartitioning(dto12)
            _ <- timesCalled(dto11, 1)
            _ <- timesCalled(dto12, 1)
          } yield assertTrue(
            r11 == Created(expected(storedAuthor)),
            r12 == Created(expected(storedAuthor))
          )
        },
        test("statuses 13 (existing + new parent) and 14 (existing, nothing written) -> Existing") {
          for {
            r13 <- PartitioningRepository.createPartitioning(dto13)
            r14 <- PartitioningRepository.createPartitioning(dto14)
            _ <- timesCalled(dto13, 1)
            _ <- timesCalled(dto14, 1)
          } yield assertTrue(
            r13 == Existing(expected(storedAuthor)),
            r14 == Existing(expected(storedAuthor))
          )
        },
        test("Existing (status 14) carries the STORED author, not the requester's") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTOExistingAuthor)
          } yield assertTrue(
            partitioningSubmitV2DTOExistingAuthor.author == "requester",
            result.data.author == "stored-author",
            result.data.author != partitioningSubmitV2DTOExistingAuthor.author,
            result.data.partitioning == partitioningSubmitV2DTOExistingAuthor.partitioning
          )
        },
        test("Returns expected ConflictDatabaseError (cycle, status 32)") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTO2).exit
          } yield assertTrue(
            result == Exit.fail(
              ConflictDatabaseError(
                "Exception caused by operation: 'createPartitioning': (32) Cycle detected"
              )
            )
          )
        },
        test("with-parents status 32 maps to ConflictDatabaseError") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTOMultiParentCycle).exit
          } yield assertTrue(
            result == Exit.fail(
              ConflictDatabaseError("Exception caused by operation: 'createPartitioning': (32) Cycle detected")
            )
          )
        },
        test("status 41 maps to NotFoundDatabaseError") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTOMultiParentNotFound).exit
          } yield assertTrue(
            result == Exit.fail(
              NotFoundDatabaseError(
                "Exception caused by operation: 'createPartitioning': (41) Parent partitioning not found"
              )
            )
          )
        },
        test("a failing DB task maps to GeneralDatabaseError") {
          for {
            result <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTO3).exit
            resultN <- PartitioningRepository.createPartitioning(partitioningSubmitV2DTOMultiParentBoom).exit
          } yield assertTrue(
            result == Exit.fail(
              GeneralDatabaseError("Operation 'createPartitioning' failed with unexpected error: boom!")
            ),
            resultN == Exit.fail(
              GeneralDatabaseError("Operation 'createPartitioning' failed with unexpected error: boom!")
            )
          )
        }
      ),
      suite("CreateOrUpdateAdditionalDataSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningRepository.createOrUpdateAdditionalData(1L, additionalDataPatchDTO1)
          } yield assertTrue(result.isInstanceOf[Seq[AdditionalDataItemV2DTO]])
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
            result == Seq(AdditionalDataItemV2DTO("key", Some("value"), "author"))
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
            result <- PartitioningRepository.getFlowPartitionings(1L, 10, 0L)
          } yield assertTrue(result == ResultNoMore(Seq(partitioningWithIdDTO1)))
        },
        test("Returns expected ResultHasMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningRepository.getFlowPartitionings(2L, 10, 0L)
          } yield assertTrue(result == ResultHasMore(Seq(partitioningWithIdDTO2)))
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getFlowPartitionings(0L, 10, 0L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getFlowPartitionings(3L, 10, 0L).exit)(
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
      suite("UpdatePartitioningParentSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningRepository.updatePartitioningParent(1L, partitioningParentPatchDTO1)
          } yield assertTrue(result == ())
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.updatePartitioningParent(1L, partitioningParentPatchDTO5).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.updatePartitioningParent(0L, partitioningParentPatchDTO1).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.updatePartitioningParent(3L, partitioningParentPatchDTO1).exit)(
            failsWithA[GeneralDatabaseError]
          )
        },
        test("Status 32 (cycle) maps to ConflictDatabaseError and logs exactly one PATCH WARN") {
          for {
            exit <- PartitioningRepository.updatePartitioningParent(5L, cyclePatchDTO).exit
            warnings <- ZTestLogger.logOutput.map(_.filter(isPatchWarning(5L)))
          } yield assert(exit)(failsWithA[ConflictDatabaseError]) &&
            assertTrue(
              warnings.size == 1,
              warnings.head.message().contains("cycle"),
              warnings.head.message().contains("partitioning 5")
            )
        },
        test("Status 33 (descendants) maps to ConflictDatabaseError and logs exactly one PATCH WARN") {
          for {
            exit <- PartitioningRepository.updatePartitioningParent(6L, descendantsPatchDTO).exit
            warnings <- ZTestLogger.logOutput.map(_.filter(isPatchWarning(6L)))
          } yield assert(exit)(failsWithA[ConflictDatabaseError]) &&
            assertTrue(warnings.size == 1, warnings.head.message().contains("descendants"))
        },
        test("A non-conflict failure logs no PATCH WARN") {
          for {
            exit <- PartitioningRepository.updatePartitioningParent(3L, partitioningParentPatchDTO1).exit
            warnings <- ZTestLogger.logOutput.map(_.filter(isPatchWarning(3L)))
          } yield assert(exit)(failsWithA[GeneralDatabaseError]) && assertTrue(warnings.isEmpty)
        }
      ),
      suite("GetPartitioningDescendantsSuite")(
        test("Returns expected ResultNoMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningRepository.getPartitioningDescendants(1L, 10, 0L)
          } yield assertTrue(result == ResultNoMore(Seq(partitioningWithIdDTO1)))
        },
        test("Returns expected ResultHasMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningRepository.getPartitioningDescendants(1111L, 10, 0L)
          } yield assertTrue(result == ResultHasMore(Seq(partitioningWithIdDTO1)))
        },
        test("No descendants (status 14) -> empty ResultNoMore") {
          for {
            result <- PartitioningRepository.getPartitioningDescendants(5555L, 10, 0L)
          } yield assertTrue(result == ResultNoMore(Seq.empty[PartitioningWithIdDTO]))
        },
        test("Status 41 -> NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningDescendants(9999L, 10, 0L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        }
      ),
      suite("GetPartitioningAncestorsSuite")(
        test("Returns expected ResultNoMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningRepository.getPartitioningAncestors(1L, 10, 0L)
          } yield assertTrue(result == ResultNoMore(Seq(partitioningWithIdDTO1)))
        },
        test("Returns expected ResultHasMore[PartitioningWithIdDTO]") {
          for {
            result <- PartitioningRepository.getPartitioningAncestors(1111L, 10, 0L)
          } yield assertTrue(result == ResultHasMore(Seq(partitioningWithIdDTO2)))
        },
        test("Returns all ancestors of a multi-parent child once with ResultNoMore") {
          for {
            result <- PartitioningRepository.getPartitioningAncestors(3333L, 10, 0L)
          } yield assertTrue(
            result == ResultNoMore(Seq(partitioningWithIdDTO1, partitioningWithIdDTO2)),
            result.data.map(_.id).distinct.size == result.data.size
          )
        },
        test("Returns all ancestors of a multi-parent child once with ResultHasMore") {
          for {
            result <- PartitioningRepository.getPartitioningAncestors(4444L, 10, 0L)
          } yield assertTrue(
            result == ResultHasMore(Seq(partitioningWithIdDTO1, partitioningWithIdDTO2)),
            result.data.map(_.id).distinct.size == result.data.size
          )
        },
        test("An existing partitioning with no ancestors (status 14 row without data) -> empty ResultNoMore") {
          for {
            result <- PartitioningRepository.getPartitioningAncestors(5555L, 10, 0L)
          } yield assertTrue(result == ResultNoMore(Seq.empty[PartitioningWithIdDTO]))
        },
        test("No rows at all -> empty ResultNoMore") {
          for {
            result <- PartitioningRepository.getPartitioningAncestors(6666L, 10, 0L)
          } yield assertTrue(result == ResultNoMore(Seq.empty[PartitioningWithIdDTO]))
        },
        test("Returns expected NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningAncestors(9999L, 10, 0L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningAncestors(8888L, 10, 0L).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetDirectParentsSuite")(
        test("Returns direct parent for partitioning") {
          for {
            result <- PartitioningRepository.getPartitioningParents(1L)
          } yield assertTrue(result == Seq(directParentDTO1))
        },
        test("Returns multiple direct parents for partitioning") {
          for {
            result <- PartitioningRepository.getPartitioningParents(3333L)
          } yield assertTrue(result == Seq(directParentDTO1, directParentDTO2))
        },
        test("No rows at all -> empty Seq") {
          for {
            result <- PartitioningRepository.getPartitioningParents(6666L)
          } yield assertTrue(result.isEmpty)
        },
        test("Returns expected NotFoundDatabaseError when partitioning not found") {
          assertZIO(PartitioningRepository.getPartitioningParents(9999L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected GeneralDatabaseError on DB failure") {
          assertZIO(PartitioningRepository.getPartitioningParents(8888L).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetPartitioningChildrenSuite")(
        test("Partitioning with 2 direct children -> 2 ChildPartitioningDTOs") {
          for {
            result <- PartitioningRepository.getPartitioningChildren(3333L)
          } yield assertTrue(result == Seq(directChildDTO1, directChildDTO2))
        },
        test("No children at all -> empty Seq") {
          for {
            result <- PartitioningRepository.getPartitioningChildren(6666L)
          } yield assertTrue(result.isEmpty)
        },
        test("Returns expected NotFoundDatabaseError when partitioning not found") {
          assertZIO(PartitioningRepository.getPartitioningChildren(9999L).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("Returns expected GeneralDatabaseError on DB failure") {
          assertZIO(PartitioningRepository.getPartitioningChildren(8888L).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      ),
      suite("GetPartitioningLineageGraphSuite")(
        test("Status 11 -> returns LineageGraphDTO with nodes and edges") {
          for {
            result <- PartitioningRepository.getPartitioningLineageGraph(2L, "both", 5)
          } yield assertTrue(
            result.primaryPartitioningId == 2L,
            result.nodes.size == 2,
            result.edges.size == 1
          )
        },
        test("Status 41 -> NotFoundDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningLineageGraph(9999L, "both", 5).exit)(
            failsWithA[NotFoundDatabaseError]
          )
        },
        test("DB failure -> GeneralDatabaseError") {
          assertZIO(PartitioningRepository.getPartitioningLineageGraph(8888L, "both", 5).exit)(
            failsWithA[GeneralDatabaseError]
          )
        }
      )
    )
  }.provide(
    PartitioningRepositoryImpl.layer,
    createPartitioningWithParentsMockLayer,
    getPartitioningMeasuresMockLayer,
    createOrUpdateAdditionalDataMockLayer,
    getPartitioningByIdByIdMockLayer,
    getPartitioningAdditionalDataMockLayer,
    getPartitioningMockLayer,
    getPartitioningMeasuresV2MockLayer,
    getFlowPartitioningsMockLayer,
    getPartitioningMainFlowMockLayer,
    updatePartitioningParentMockLayer,
    getPartitioningAncestorsMockLayer,
    getPartitioningDescendantsMockLayer,
    getDirectParentsMockLayer,
    getDirectChildrenMockLayer,
    getPartitioningLineageGraphMockLayer
  )

}

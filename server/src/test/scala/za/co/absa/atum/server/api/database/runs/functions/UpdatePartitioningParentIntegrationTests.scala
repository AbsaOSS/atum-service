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

package za.co.absa.atum.server.api.database.runs.functions

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.functions.UpdatePartitioningParent.UpdatePartitioningParentArgs
import za.co.absa.db.fadb.exceptions.{DataConflictException, DataNotFoundException}
import za.co.absa.db.fadb.status.FunctionStatus
import zio._
import zio.interop.catz.asyncInstance
import zio.test._

object UpdatePartitioningParentIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("UpdatePartitioningParentSuite")(
      test("Returns expected Left with DataNotFoundException (42) when child partitioning is not in the database") {
        val partitioningParentPatchDTO = PartitioningParentPatchDTO(
          parentPartitioningId = Long.MaxValue,
          author = "author"
        )
        for {
          updatePartitioningParent <- ZIO.service[UpdatePartitioningParent]
          result <- updatePartitioningParent(UpdatePartitioningParentArgs(Long.MaxValue, partitioningParentPatchDTO))
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(42, "Child Partitioning not found"))))
      },
      test("Returns expected Left with DataNotFoundException (41) when parent partitioning is not in the database") {
        val partitioning = Seq(PartitionDTO("patchMissingParentKey", s"child-${java.util.UUID.randomUUID()}"))
        for {
          create <- ZIO.service[CreatePartitioningWithParents]
          updatePartitioningParent <- ZIO.service[UpdatePartitioningParent]
          created <- create(PartitioningSubmitV2DTO(partitioning, Seq.empty[Long], "author"))
          childId = created.toOption.get.data.get.id
          result <- updatePartitioningParent(
            UpdatePartitioningParentArgs(childId, PartitioningParentPatchDTO(Long.MaxValue, "author"))
          )
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Parent Partitioning not found"))))
      },
      test("Returns Right with status 11 when linking an existing child under an existing parent") {
        val parentPart = Seq(PartitionDTO("patchOkParent", s"p-${java.util.UUID.randomUUID()}"))
        val childPart = Seq(PartitionDTO("patchOkChild", s"c-${java.util.UUID.randomUUID()}"))
        for {
          create <- ZIO.service[CreatePartitioningWithParents]
          updatePartitioningParent <- ZIO.service[UpdatePartitioningParent]
          parentRes <- create(PartitioningSubmitV2DTO(parentPart, Seq.empty[Long], "author"))
          childRes <- create(PartitioningSubmitV2DTO(childPart, Seq.empty[Long], "author"))
          parentId = parentRes.toOption.get.data.get.id
          childId = childRes.toOption.get.data.get.id
          result <- updatePartitioningParent(
            UpdatePartitioningParentArgs(childId, PartitioningParentPatchDTO(parentId, "linker"))
          )
        } yield assertTrue(
          result.isRight,
          result.toOption.get.functionStatus == FunctionStatus(11, "OK")
        )
      },
      test("Returns Left (status 32) when an existing partitioning is linked under itself (self-link)") {
        val partitioning = Seq(PartitionDTO("patchSelfKey", s"self-${java.util.UUID.randomUUID()}"))
        for {
          create <- ZIO.service[CreatePartitioningWithParents]
          updatePartitioningParent <- ZIO.service[UpdatePartitioningParent]
          created <- create(PartitioningSubmitV2DTO(partitioning, Seq.empty[Long], "author"))
          id = created.toOption.get.data.get.id
          result <- updatePartitioningParent(
            UpdatePartitioningParentArgs(id, PartitioningParentPatchDTO(parentPartitioningId = id, author = "author"))
          )
        } yield assertTrue(result.left.exists {
          case DataConflictException(status) => status.statusCode == 32
          case _                             => false
        })
      }
    ).provide(
      CreatePartitioningWithParents.layer,
      UpdatePartitioningParent.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithSingleTransactionRollback
    ) @@ TestAspect.sequential
  }


}

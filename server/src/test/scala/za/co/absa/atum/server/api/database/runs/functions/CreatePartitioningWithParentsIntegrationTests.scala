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

import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningSubmitV2DTO}
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.db.fadb.exceptions.{DataConflictException, DataNotFoundException}
import zio._
import zio.interop.catz.asyncInstance
import zio.test._

object CreatePartitioningWithParentsIntegrationTests extends ConfigProviderTest {

  private def uniquePartitioning(tag: String) =
    Seq(PartitionDTO("withParentsKey", s"$tag-${java.util.UUID.randomUUID()}"))

  private def create(partitioning: Seq[PartitionDTO], parents: Seq[Long], author: String = "author") =
    ZIO.serviceWithZIO[CreatePartitioningWithParents](_(PartitioningSubmitV2DTO(partitioning, parents, author)))

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CreatePartitioningWithParentsSuite")(
      test("New partitioning with 0 parents -> status 11, id and requester stored as created_by") {
        for {
          result <- create(uniquePartitioning("a"), Seq.empty[Long], author = "requesterA")
        } yield assertTrue(
          result.isRight,
          result.toOption.get.functionStatus.statusCode == 11,
          result.toOption.get.data.id > 0L,
          result.toOption.get.data.createdBy == "requesterA",
          result.toOption.get.data.wasCreated
        )
      },
      test("Returns Left with DataNotFoundException as an unknown parent is not in the database") {
        for {
          result <- create(uniquePartitioning("f"), Seq(Long.MaxValue))
        } yield assertTrue(result.left.exists {
          case DataNotFoundException(status) => status.statusCode == 41
          case _ => false
        })
      },
      test("Returns Left with DataConflictException when linking would create a cycle") {
        val keyC = uniquePartitioning("g")
        for {
          c <- create(keyC, Seq.empty[Long])
          cId = c.toOption.get.data.id
          p <- create(uniquePartitioning("gP"), Seq(cId)) // P is a child of C
          result <- create(keyC, Seq(p.toOption.get.data.id)) // would make C a child of its own descendant
        } yield assertTrue(result.left.exists {
          case DataConflictException(status) => status.statusCode == 32
          case _ => false
        })
      }
    ).provide(
      CreatePartitioningWithParents.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithSingleTransactionRollback
    )
  }

}

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

  private def create(
    partitioning: Seq[PartitionDTO],
    parents: Seq[Long],
    author: String = "author",
    copyMeasureDefinitions: Boolean = false
  ) =
    ZIO.serviceWithZIO[CreatePartitioningWithParents](
      _(PartitioningSubmitV2DTO(partitioning, parents, author, copyMeasureDefinitions))
    )

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CreatePartitioningWithParentsSuite")(
      test("New partitioning with 0 parents -> status 11, id and requester stored as created_by") {
        for {
          result <- create(uniquePartitioning("a"), Seq.empty[Long], author = "requesterA")
        } yield assertTrue(
          result.isRight,
          result.toOption.get.functionStatus.statusCode == 11,
          result.toOption.get.data.exists(_.id > 0L),
          result.toOption.get.data.map(_.createdBy).contains("requesterA"),
          result.toOption.get.data.map(_.wasCreated).contains(true)
        )
      },
      test("New partitioning with parents -> status 12, wasCreated = true") {
        for {
          p1 <- create(uniquePartitioning("p1"), Seq.empty[Long], author = "parentAuthor")
          p1Id = p1.toOption.get.data.get.id
          child <- create(uniquePartitioning("c12"), Seq(p1Id), author = "childAuthor")
        } yield assertTrue(
          child.isRight,
          child.toOption.get.functionStatus.statusCode == 12,
          child.toOption.get.data.exists(_.id > 0L),
          child.toOption.get.data.map(_.createdBy).contains("childAuthor"),
          child.toOption.get.data.map(_.wasCreated).contains(true)
        )
      },
      test("Existing partitioning gets new parent -> status 13, wasCreated = false, stored author preserved") {
        val childKey = uniquePartitioning("c13")
        for {
          p1 <- create(uniquePartitioning("p13"), Seq.empty[Long], author = "parentAuthor")
          c0 <- create(childKey, Seq.empty[Long], author = "originalAuthor")
          c1 <- create(childKey, Seq(p1.toOption.get.data.get.id), author = "laterCaller")
        } yield assertTrue(
          c1.isRight,
          c1.toOption.get.functionStatus.statusCode == 13,
          c1.toOption.get.data.map(_.id) == c0.toOption.get.data.map(_.id),
          c1.toOption.get.data.map(_.createdBy).contains("originalAuthor"),
          c1.toOption.get.data.map(_.wasCreated).contains(false)
        )
      },
      test("Idempotent re-create with same parents -> status 14, wasCreated = false") {
        val childKey = uniquePartitioning("c14")
        for {
          p1 <- create(uniquePartitioning("p14"), Seq.empty[Long], author = "parentAuthor")
          p1Id = p1.toOption.get.data.get.id
          c0 <- create(childKey, Seq(p1Id), author = "originalAuthor")
          c1 <- create(childKey, Seq(p1Id), author = "retryCaller")
        } yield assertTrue(
          c1.isRight,
          c1.toOption.get.functionStatus.statusCode == 14,
          c1.toOption.get.data.map(_.id) == c0.toOption.get.data.map(_.id),
          c1.toOption.get.data.map(_.createdBy).contains("originalAuthor"),
          c1.toOption.get.data.map(_.wasCreated).contains(false)
        )
      },
      test("New partitioning with copyMeasureDefinitions = true succeeds with status 12") {
        for {
          p1 <- create(uniquePartitioning("pmd"), Seq.empty[Long], author = "parentAuthor")
          p1Id = p1.toOption.get.data.get.id
          child <- create(uniquePartitioning("cmd"), Seq(p1Id), author = "childAuthor", copyMeasureDefinitions = true)
        } yield assertTrue(
          child.isRight,
          child.toOption.get.functionStatus.statusCode == 12,
          child.toOption.get.data.map(_.wasCreated).contains(true)
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
      test("Returns Left with DataConflictException when linking would create a cycle (status 32)") {
        val keyC = uniquePartitioning("g")
        for {
          c <- create(keyC, Seq.empty[Long])
          cId = c.toOption.get.data.get.id
          p <- create(uniquePartitioning("gP"), Seq(cId)) // P is a child of C
          result <- create(keyC, Seq(p.toOption.get.data.get.id)) // would make C a child of its own descendant
        } yield assertTrue(result.left.exists {
          case DataConflictException(status) => status.statusCode == 32
          case _ => false
        })
      },
      test("Returns Left with DataConflictException when existing partitioning already has descendants (status 33)") {
        val keyC = uniquePartitioning("hC")
        for {
          c <- create(keyC, Seq.empty[Long])
          cId = c.toOption.get.data.get.id
          _ <- create(uniquePartitioning("hD"), Seq(cId))
          n <- create(uniquePartitioning("hN"), Seq.empty[Long])
          result <- create(keyC, Seq(n.toOption.get.data.get.id))
        } yield assertTrue(result.left.exists {
          case DataConflictException(status) => status.statusCode == 33
          case _ => false
        })
      }
    ).provide(
      CreatePartitioningWithParents.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithSingleTransactionRollback
    ) @@ TestAspect.sequential
  }

}

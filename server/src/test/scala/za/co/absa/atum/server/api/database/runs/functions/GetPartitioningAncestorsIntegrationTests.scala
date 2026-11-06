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
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningAncestors.GetPartitioningAncestorsArgs
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio.{Scope, ZIO}
import zio.test.{Spec, TestEnvironment, TestAspect, assertTrue}
import zio.interop.catz.asyncInstance

/**
 *  Runs the REAL `GetPartitioningAncestors` fa-db wrapper against `runs.get_partitioning_ancestors`.
 *  Status contract: 10 (one row per ancestor), 14 (existing partitioning WITHOUT ancestors: one row with all data
 *  columns NULL), 41 (unknown partitioning). Plain fa-db 0.7.0 failed with "Status is OK but data is missing" on
 *  the 14 row; the wrapper now yields it as a row without data. All tests share one rolled-back transaction.
 */
object GetPartitioningAncestorsIntegrationTests extends ConfigProviderTest {

  private def uniquePartitioning(tag: String) =
    Seq(PartitionDTO("ancestorsKey", s"$tag-${java.util.UUID.randomUUID()}"))

  private def create(tag: String, parents: Seq[Long]) =
    ZIO
      .serviceWithZIO[CreatePartitioningWithParents](
        _(PartitioningSubmitV2DTO(uniquePartitioning(tag), parents, "author"))
      )
      .map(_.toOption.get.data.id)

  private def ancestors(partitioningId: Long, limit: Int = 100, offset: Long = 0L) =
    ZIO.serviceWithZIO[GetPartitioningAncestors](_(GetPartitioningAncestorsArgs(partitioningId, limit, offset)))

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {
    suite("GetPartitioningAncestorsIntegrationTests")(
      test("Retrieve Ancestors' partitions for a given id") {
        val partitioningID: Long = 1111L
        for {
          getPartitioningAncestors <- ZIO.service[GetPartitioningAncestors]
          result <- getPartitioningAncestors(GetPartitioningAncestorsArgs(partitioningID, 10, 0L))

        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      },
      test("unknown partitioning -> Left(DataNotFoundException 41), never an OK-without-data failure") {
        for {
          result <- ancestors(Long.MaxValue)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      },
      test("existing partitioning WITHOUT ancestors -> Right with only the status 14 row, no data (no exception)") {
        for {
          id <- create("noAncestors", Seq.empty[Long])
          result <- ancestors(id)
        } yield assertTrue(
          result.toOption.get.map(r => (r.functionStatus, r.data)) == Seq((FunctionStatus(14, "OK"), None)),
          result.toOption.get.flatMap(_.data).isEmpty
        )
      },
      test("existing partitioning with one ancestor -> one status 10 row with that ancestor") {
        for {
          parent <- create("singleParent", Seq.empty[Long])
          child <- create("singleChild", Seq(parent))
          result <- ancestors(child)
          rows = result.toOption.get
        } yield assertTrue(
          rows.size == 1,
          rows.head.functionStatus.statusCode == 10,
          rows.head.data.map(_.id) == Some(parent),
          rows.head.data.map(_.author) == Some("author"),
          rows.head.data.map(_.hasMore) == Some(false)
        )
      },
      test("limit smaller than the number of ancestors -> limited rows and hasMore = true") {
        for {
          p1 <- create("pageP1", Seq.empty[Long])
          p2 <- create("pageP2", Seq.empty[Long])
          child <- create("pageChild", Seq(p1, p2))
          page1 <- ancestors(child, limit = 1)
        } yield assertTrue(
          page1.toOption.get.flatMap(_.data).map(_.id) == Seq(p1.min(p2)),
          page1.toOption.get.flatMap(_.data).map(_.hasMore) == Seq(true)
        )
      },
      test("offset beyond the last ancestor -> Right with an empty data set (no exception)") {
        for {
          parent <- create("offsetParent", Seq.empty[Long])
          child <- create("offsetChild", Seq(parent))
          result <- ancestors(child, limit = 10, offset = 5L)
        } yield assertTrue(result.isRight, result.toOption.get.flatMap(_.data).isEmpty)
      }
    ) @@ TestAspect.sequential
  }.provide(
    GetPartitioningAncestors.layer,
    CreatePartitioningWithParents.layer,
    PostgresDatabaseProvider.layer,
    TestTransactorProvider.layerWithSingleTransactionRollback
  )
}

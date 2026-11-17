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

package za.co.absa.atum.server.api.database.lineage.functions

import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningDescendants.GetPartitioningDescendantsArgs
import za.co.absa.atum.server.api.database.runs.functions.CreatePartitioningWithParents
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio.{Scope, ZIO}
import zio.interop.catz.asyncInstance
import zio.test.{Spec, TestAspect, TestEnvironment, assertTrue}

/**
 *  Runs the real `GetPartitioningDescendants` fa-db wrapper against `lineage.get_partitioning_descendants`.
 */
object GetPartitioningDescendantsIntegrationTests extends ConfigProviderTest with LineageFunctionIntegrationTestSupport {

  override protected val partitioningKeyName: String = "descendantsKey"

  private def descendants(partitioningId: Long, limit: Int = 100, offset: Long = 0L) =
    ZIO.serviceWithZIO[GetPartitioningDescendants](_(GetPartitioningDescendantsArgs(partitioningId, limit, offset)))

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {
    suite("GetPartitioningDescendantsIntegrationTests")(
      test("unknown partitioning -> Left(DataNotFoundException 41)") {
        for {
          result <- descendants(Long.MaxValue)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      },
      test("existing partitioning WITHOUT descendants -> Right with status 14 row and no data") {
        for {
          id <- create("leaf", Seq.empty[Long])
          result <- descendants(id)
        } yield assertTrue(
          result.toOption.get.map(r => (r.functionStatus, r.data)) == Seq((FunctionStatus(14, "OK"), None)),
          result.toOption.get.flatMap(_.data).isEmpty
        )
      },
      test("transitive descendants -> Right with status 10 rows") {
        for {
          root <- create("root", Seq.empty[Long])
          child <- create("child", Seq(root))
          grandchild <- create("grandchild", Seq(child))
          result <- descendants(root)
          rows = result.toOption.get
        } yield assertTrue(
          rows.size == 2,
          rows.forall(_.functionStatus.statusCode == 10),
          rows.flatMap(_.data).map(_.id) == Seq(child, grandchild).sorted,
          rows.flatMap(_.data).forall(!_.hasMore)
        )
      },
      test("limit smaller than number of descendants -> limited rows and hasMore = true") {
        for {
          root <- create("pageRoot", Seq.empty[Long])
          c1 <- create("pageC1", Seq(root))
          c2 <- create("pageC2", Seq(root))
          page1 <- descendants(root, limit = 1, offset = 0L)
          page2 <- descendants(root, limit = 1, offset = 1L)
        } yield assertTrue(
          page1.toOption.get.flatMap(_.data).map(_.id) == Seq(c1.min(c2)),
          page1.toOption.get.flatMap(_.data).map(_.hasMore) == Seq(true),
          page2.toOption.get.flatMap(_.data).map(_.id) == Seq(c1.max(c2)),
          page2.toOption.get.flatMap(_.data).map(_.hasMore) == Seq(false)
        )
      },
      test("offset beyond the last descendant -> Right with empty data set") {
        for {
          root <- create("offsetRoot", Seq.empty[Long])
          _ <- create("offsetChild", Seq(root))
          result <- descendants(root, limit = 10, offset = 5L)
        } yield assertTrue(result.isRight, result.toOption.get.flatMap(_.data).isEmpty)
      }
    ) @@ TestAspect.sequential
  }.provide(
    GetPartitioningDescendants.layer,
    CreatePartitioningWithParents.layer,
    PostgresDatabaseProvider.layer,
    TestTransactorProvider.layerWithSingleTransactionRollback
  )
}

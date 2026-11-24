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
import za.co.absa.atum.server.api.database.runs.functions.CreatePartitioningWithParents
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio.{Scope, ZIO}
import zio.interop.catz.asyncInstance
import zio.test.{Spec, TestAspect, TestEnvironment, assertTrue}

/**
 *  Runs the real `GetDirectChildren` fa-db wrapper against `lineage.get_direct_children`.
 */
object GetDirectChildrenIntegrationTests extends ConfigProviderTest with LineageFunctionIntegrationTestSupport {

  override protected val partitioningKeyName: String = "directChildrenKey"

  private def directChildren(partitioningId: Long) =
    ZIO.serviceWithZIO[GetDirectChildren](_(partitioningId))

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {
    suite("GetDirectChildrenIntegrationTests")(
      test("unknown partitioning -> Left(DataNotFoundException 41)") {
        for {
          result <- directChildren(Long.MaxValue)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      },
      test("existing partitioning without children -> Right with empty rows") {
        for {
          id <- create("leaf", Seq.empty[Long])
          result <- directChildren(id)
        } yield assertTrue(result.isRight, result.toOption.get.flatMap(_.data).isEmpty)
      },
      test("existing partitioning with multiple direct children -> Right with status 11 rows (excludes grandchildren)") {
        for {
          parent <- create("parent", Seq.empty[Long])
          c1 <- create("c1", Seq(parent), author = "child1Author")
          c2 <- create("c2", Seq(parent), author = "child2Author")
          _ <- create("grandchild", Seq(c1), author = "gcAuthor")
          result <- directChildren(parent)
          rows = result.toOption.get
          data = rows.flatMap(_.data)
        } yield assertTrue(
          rows.size == 2,
          rows.forall(_.functionStatus.statusCode == 11),
          data.map(_.id) == Seq(c1, c2).sorted,
          data.forall(_.linkedAt.isDefined)
        )
      }
    ) @@ TestAspect.sequential
  }.provide(
    GetDirectChildren.layer,
    CreatePartitioningWithParents.layer,
    PostgresDatabaseProvider.layer,
    TestTransactorProvider.layerWithSingleTransactionRollback
  )
}

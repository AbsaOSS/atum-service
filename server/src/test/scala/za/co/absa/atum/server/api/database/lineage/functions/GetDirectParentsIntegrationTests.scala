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
 *  Runs the real `GetDirectParents` fa-db wrapper against `lineage.get_direct_parents`.
 */
object GetDirectParentsIntegrationTests extends ConfigProviderTest with LineageFunctionIntegrationTestSupport {

  override protected val partitioningKeyName: String = "directParentsKey"

  private def directParents(partitioningId: Long) =
    ZIO.serviceWithZIO[GetDirectParents](_(partitioningId))

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {
    suite("GetDirectParentsIntegrationTests")(
      test("unknown partitioning -> Left(DataNotFoundException 41)") {
        for {
          result <- directParents(Long.MaxValue)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      },
      test("existing partitioning without parents -> Right with empty rows") {
        for {
          id <- create("root", Seq.empty[Long])
          result <- directParents(id)
        } yield assertTrue(result.isRight, result.toOption.get.flatMap(_.data).isEmpty)
      },
      test("existing partitioning with multiple parents -> Right with status 11 rows and edge metadata") {
        for {
          p1 <- create("p1", Seq.empty[Long], author = "author1")
          p2 <- create("p2", Seq.empty[Long], author = "author2")
          child <- create("child", Seq(p1, p2), author = "linkerUser")
          result <- directParents(child)
          rows = result.toOption.get
          data = rows.flatMap(_.data)
        } yield assertTrue(
          rows.size == 2,
          rows.forall(_.functionStatus.statusCode == 11),
          data.map(_.id) == Seq(p1, p2).sorted,
          data.forall(_.linkedBy.contains("linkerUser")),
          data.forall(_.linkedAt.isDefined)
        )
      }
    ) @@ TestAspect.sequential
  }.provide(
    GetDirectParents.layer,
    CreatePartitioningWithParents.layer,
    PostgresDatabaseProvider.layer,
    TestTransactorProvider.layerWithSingleTransactionRollback
  )
}

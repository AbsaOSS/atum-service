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
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningLineageGraph.GetPartitioningLineageGraphArgs
import za.co.absa.atum.server.api.database.runs.functions.CreatePartitioningWithParents
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio.{Scope, ZIO}
import zio.interop.catz.asyncInstance
import zio.test.{Spec, TestAspect, TestEnvironment, assertTrue}

/**
 *  Runs the real `GetPartitioningLineageGraph` fa-db wrapper against `lineage.get_partitioning_lineage_graph`.
 */
object GetPartitioningLineageGraphIntegrationTests extends ConfigProviderTest with LineageFunctionIntegrationTestSupport {

  override protected val partitioningKeyName: String = "lineageGraphKey"

  private def lineageGraph(partitioningId: Long, direction: String = "both", depth: Int = 5) =
    ZIO.serviceWithZIO[GetPartitioningLineageGraph](
      _(GetPartitioningLineageGraphArgs(partitioningId, direction, depth))
    )

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {
    suite("GetPartitioningLineageGraphIntegrationTests")(
      test("unknown partitioning -> Left(DataNotFoundException 41)") {
        for {
          result <- lineageGraph(Long.MaxValue)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      },
      test("isolated partitioning -> 1 node and 0 edges") {
        for {
          id <- create("isolated", Seq.empty[Long])
          result <- lineageGraph(id)
          dto = result.toOption.get.data.get.toLineageGraphDTO.toOption.get
        } yield assertTrue(
          result.toOption.get.functionStatus == FunctionStatus(11, "OK"),
          dto.primaryPartitioningId == id,
          dto.nodes.map(_.id) == Seq(id),
          dto.edges.isEmpty
        )
      },
      test("chain A -> B -> C returns full graph for 'both' and filtered graphs for 'upstream'/'downstream'") {
        for {
          a <- create("graphA", Seq.empty[Long], author = "authorA")
          b <- create("graphB", Seq(a), author = "authorB")
          c <- create("graphC", Seq(b), author = "authorC")
          bothRes <- lineageGraph(b, "both", 5)
          upRes <- lineageGraph(b, "upstream", 5)
          downRes <- lineageGraph(b, "downstream", 5)
          bothDto = bothRes.toOption.get.data.get.toLineageGraphDTO.toOption.get
          upDto = upRes.toOption.get.data.get.toLineageGraphDTO.toOption.get
          downDto = downRes.toOption.get.data.get.toLineageGraphDTO.toOption.get
        } yield assertTrue(
          bothDto.nodes.map(_.id).toSet == Set(a, b, c),
          bothDto.edges.map(e => (e.parentPartitioningId, e.childPartitioningId)).toSet == Set((a, b), (b, c)),
          upDto.nodes.map(_.id).toSet == Set(a, b),
          upDto.edges.map(e => (e.parentPartitioningId, e.childPartitioningId)).toSet == Set((a, b)),
          downDto.nodes.map(_.id).toSet == Set(b, c),
          downDto.edges.map(e => (e.parentPartitioningId, e.childPartitioningId)).toSet == Set((b, c))
        )
      }
    ) @@ TestAspect.sequential
  }.provide(
    GetPartitioningLineageGraph.layer,
    CreatePartitioningWithParents.layer,
    PostgresDatabaseProvider.layer,
    TestTransactorProvider.layerWithSingleTransactionRollback
  )
}

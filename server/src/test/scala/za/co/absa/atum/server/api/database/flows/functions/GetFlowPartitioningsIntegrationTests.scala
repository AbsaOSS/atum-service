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

package za.co.absa.atum.server.api.database.flows.functions

import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningSubmitV2DTO}
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings.GetFlowPartitioningsArgs
import za.co.absa.atum.server.api.database.runs.functions.{CreatePartitioning, GetPartitioningMainFlow}
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio.{Scope, ZIO}
import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.interop.catz.asyncInstance

import java.util.UUID

object GetFlowPartitioningsIntegrationTests extends ConfigProviderTest {

  private def createPartitioning(parentId: Option[Long]): ZIO[CreatePartitioning, Throwable, Long] =
    ZIO
      .serviceWithZIO[CreatePartitioning](
        _(PartitioningSubmitV2DTO(Seq(PartitionDTO("page", UUID.randomUUID().toString)), parentId, "Pagination author"))
      )
      .flatMap(ZIO.fromEither(_))
      .map(_.data)

  private def getPage(flowId: Long, offset: Long): ZIO[GetFlowPartitionings, Throwable, Seq[(Long, Boolean)]] =
    ZIO
      .serviceWithZIO[GetFlowPartitionings](_(GetFlowPartitioningsArgs(flowId, 2, offset)))
      .flatMap(ZIO.fromEither(_))
      .flatMap(rows => ZIO.foreach(rows)(row => ZIO.getOrFail(row.data)))
      .map(_.map(row => (row.id, row.hasMore)))

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {
    suite("GetFlowPartitioningsIntegrationTests")(
      test("Returns expected DataNotFoundException when flow not found") {
        for {
          getFlowPartitionings <- ZIO.service[GetFlowPartitionings]
          result <- getFlowPartitionings(GetFlowPartitioningsArgs(0L, 1, 1L))
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Flow not found"))))
      },
      test("Decodes ordered partitionings and paging metadata at positive offsets") {
        for {
          parentId <- createPartitioning(None)
          childIds <- ZIO.foreach(1 to 4)(_ => createPartitioning(Some(parentId)))
          ids = (parentId +: childIds).sorted
          mainFlow <- ZIO.serviceWithZIO[GetPartitioningMainFlow](_(parentId)).flatMap(ZIO.fromEither(_))
          flowId <- ZIO.getOrFail(mainFlow.data.map(_.id))
          firstPage <- getPage(flowId, 0L)
          middlePage <- getPage(flowId, 2L)
          lastPartialPage <- getPage(flowId, 4L)
          lastFullPage <- getPage(flowId, 3L)
          beyondEndPage <- getPage(flowId, 6L)
        } yield assertTrue(
          firstPage == ids.take(2).map(id => (id, true)),
          middlePage == ids.slice(2, 4).map(id => (id, true)),
          lastPartialPage == Seq((ids.last, false)),
          lastFullPage == ids.takeRight(2).map(id => (id, false)),
          beyondEndPage.isEmpty
        )
      }
    )
  }.provide(
    GetFlowPartitionings.layer,
    CreatePartitioning.layer,
    GetPartitioningMainFlow.layer,
    PostgresDatabaseProvider.layer,
    TestTransactorProvider.layerWithSingleTransactionRollback
  )

}

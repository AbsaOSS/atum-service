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
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.{TestData, TestTransactorProvider}
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import zio._
import zio.interop.catz.asyncInstance
import zio.test._

object CreatePartitioningWithParentsIntegrationTests extends ConfigProviderTest with TestData {

  private def uniquePartitioning(tag: String) =
    Seq(PartitionDTO("withParentsKey", s"$tag-${java.util.UUID.randomUUID()}"))

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {

    suite("CreatePartitioningWithParentsSuite")(
      test("Links two existing parents and returns Right") {
        for {
          create <- ZIO.service[CreatePartitioning]
          createWithParents <- ZIO.service[CreatePartitioningWithParents]
          p1 <- create(PartitioningSubmitV2DTO(uniquePartitioning("p1"), Seq.empty[Long], "author"))
          p2 <- create(PartitioningSubmitV2DTO(uniquePartitioning("p2"), Seq.empty[Long], "author"))
          id1 = p1.toOption.get.data
          id2 = p2.toOption.get.data
          result <- createWithParents(PartitioningSubmitV2DTO(uniquePartitioning("child"), Seq(id1, id2), "author"))
        } yield assertTrue(result.isRight)
      },
      test("Returns Left (status 41) for a non-existent parent id") {
        for {
          create <- ZIO.service[CreatePartitioning]
          createWithParents <- ZIO.service[CreatePartitioningWithParents]
          p1 <- create(PartitioningSubmitV2DTO(uniquePartitioning("p1"), Seq.empty[Long], "author"))
          id1 = p1.toOption.get.data
          result <- createWithParents(
            PartitioningSubmitV2DTO(uniquePartitioning("child"), Seq(id1, Long.MaxValue), "author")
          )
        } yield assertTrue(result.left.exists {
          case DataNotFoundException(status) => status.statusCode == 41
          case _                             => false
        })
      }
    ).provide(
      CreatePartitioning.layer,
      CreatePartitioningWithParents.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithSingleTransactionRollback
    )

  }

}

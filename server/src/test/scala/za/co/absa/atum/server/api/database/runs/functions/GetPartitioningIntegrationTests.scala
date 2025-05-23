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

import za.co.absa.atum.model.dto.PartitionDTO
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.model.database.PartitioningForDB
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio.{Scope, ZIO}
import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.interop.catz.asyncInstance

object GetPartitioningIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {
    suite("GetPartitioningIntegrationTests")(
      test("GetPartitioning returns DataNotFoundException when partitioning not found") {
        for {
          getPartitioningFn <- ZIO.service[GetPartitioning]
          result <- getPartitioningFn(PartitioningForDB.fromSeqPartitionDTO(Seq.empty[PartitionDTO]))
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      }
    )
  }.provide(
    GetPartitioning.layer,
    PostgresDatabaseProvider.layer,
    TestTransactorProvider.layerWithRollback
  )
}

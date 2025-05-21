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

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.functions.UpdatePartitioningParent.UpdatePartitioningParentArgs
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio._
import zio.interop.catz.asyncInstance
import zio.test._

object UpdatePartitioningParentIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("UpdatePartitioningParentSuite")(
      test("Returns expected Left with DataNotFoundException as related partitioning is not in the database") {
        val partitioningParentPatchDTO = PartitioningParentPatchDTO(
          parentPartitioningId = 2L,
          author = "author")
        for {
          updatePartitioningParent <- ZIO.service[UpdatePartitioningParent]
          result <- updatePartitioningParent(UpdatePartitioningParentArgs(1L, partitioningParentPatchDTO))
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Child Partitioning not found"))))
      }
    ).provide(
      UpdatePartitioningParent.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }


}

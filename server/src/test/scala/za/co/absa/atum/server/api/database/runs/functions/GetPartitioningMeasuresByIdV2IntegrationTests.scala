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

import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import zio.interop.catz.asyncInstance
import za.co.absa.db.fadb.status.FunctionStatus
import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.{Scope, ZIO}

object GetPartitioningMeasuresByIdV2IntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("GetPartitioningMeasuresSuite")(
      test("Returns expected sequence of Measures with existing partitioning") {
        val partitioningID: Long = 1L

        for {
          getPartitioningMeasuresV2 <- ZIO.service[GetPartitioningMeasuresById]
          result <- getPartitioningMeasuresV2(partitioningID)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      }
    ).provide(
      GetPartitioningMeasuresById.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }

}

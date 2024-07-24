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

import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningDTO}
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.test.{Spec, TestEnvironment, assert}
import zio.{Scope, ZIO}

object GetPartitioningMeasuresIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("GetPartitioningMeasuresSuite")(
      test("Returns expected sequence of Measures with existing partitioning") {
      val partitioningDTO: PartitioningDTO = Seq(PartitionDTO("string1", "string1"), PartitionDTO("string2", "string2"))
        for {
          getPartitioningMeasures <- ZIO.service[GetPartitioningMeasures]
          result <- getPartitioningMeasures(partitioningDTO).exit
        } yield assert(result)(failsWithA[doobie.util.invariant.NonNullableColumnRead])
      }
    ).provide(
      GetPartitioningMeasures.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )

  }

}

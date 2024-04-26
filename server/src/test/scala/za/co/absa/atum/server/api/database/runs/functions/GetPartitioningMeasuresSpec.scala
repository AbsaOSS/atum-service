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

import org.junit.runner.RunWith
import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningDTO}
import za.co.absa.atum.server.ConfigProviderSpec
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import zio.test.junit.ZTestJUnitRunner
import zio.{Scope, ZIO}
import zio.test.{Spec, TestEnvironment, assertTrue}

@RunWith(classOf[ZTestJUnitRunner])
class GetPartitioningMeasuresSpec extends ConfigProviderSpec {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("GetPartitioningMeasuresSpec")(
      test("Returns expected Left for non-existing partitioning") {
        val partitioningDTO: PartitioningDTO = Seq(PartitionDTO("key1", "val1"), PartitionDTO("key2", "val2"))

        for {
          getPartitioningMeasures <- ZIO.service[GetPartitioningMeasures]
          result <- getPartitioningMeasures(partitioningDTO).either
        } yield assertTrue(result.isLeft)
      }
    ).provide(
      GetPartitioningMeasures.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )

  }

}

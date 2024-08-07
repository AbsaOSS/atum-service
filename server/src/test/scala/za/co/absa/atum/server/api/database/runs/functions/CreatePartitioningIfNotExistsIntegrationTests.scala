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

import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningSubmitDTO}
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import zio._
import zio.interop.catz.asyncInstance
import zio.test._

object CreatePartitioningIfNotExistsIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CreatePartitioningIfNotExistsSuite")(
      test("Returns expected Right with Unit") {
        val partitioningSubmitDTO = PartitioningSubmitDTO(
          partitioning = Seq(PartitionDTO("key1", "val1"), PartitionDTO("key2", "val2")),
          parentPartitioning = Some(Seq(PartitionDTO("pKey1", "pVal1"), PartitionDTO("pKey2", "pVal2"))),
          authorIfNew = "newAuthor"
        )
        for {
          createPartitioningIfNotExists <- ZIO.service[CreatePartitioningIfNotExists]
          result <- createPartitioningIfNotExists(partitioningSubmitDTO)
        } yield assertTrue(result.isRight)
      }
    ).provide(
      CreatePartitioningIfNotExists.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )

  }

}

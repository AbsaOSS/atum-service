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
import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningSubmitDTO}
import za.co.absa.atum.server.api.TestConfigProvider
import za.co.absa.atum.server.api.database.{PostgresDatabaseProvider, TransactorProvider}
import zio.{Scope, ZIO}
import zio.test._
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class CreatePartitioningIfNotExistsSpec extends ZIOSpecDefault {

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
      TransactorProvider.testLayerWithRollback
    )

  }.provideLayer(
    TestConfigProvider.layer
  )

}

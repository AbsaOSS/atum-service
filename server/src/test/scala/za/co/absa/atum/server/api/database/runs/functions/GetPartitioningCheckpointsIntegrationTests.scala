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
import za.co.absa.atum.model.dto.{CheckpointQueryDTO, PartitionDTO, PartitioningDTO}
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import zio.test.Assertion.failsWithA
import zio.{Scope, ZIO}
import zio.test._

object GetPartitioningCheckpointsIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val partitioningDTO1: PartitioningDTO = Seq(
      PartitionDTO("stringA", "stringA"),
      PartitionDTO("stringB", "stringB")
    )

    suite("GetPartitioningCheckpointsIntegrationTests")(
      test("Returns expected sequence of Checkpoints with existing partitioning") {
        val partitioningQueryDTO: CheckpointQueryDTO = CheckpointQueryDTO(
          partitioning = partitioningDTO1,
          limit = Some(10),
          checkpointName = Some("checkpointName")
        )

        for {
          getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
          exit <- getPartitioningCheckpoints(partitioningQueryDTO).exit
        } yield assert(exit)(failsWithA[doobie.util.invariant.NonNullableColumnRead])
      }
    ).provide(
      GetPartitioningCheckpoints.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }

}


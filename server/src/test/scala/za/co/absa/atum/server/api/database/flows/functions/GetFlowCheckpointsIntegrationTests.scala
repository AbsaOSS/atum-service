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

import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.model.dto.{CheckpointQueryDTO, PartitionDTO, PartitioningDTO}
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio.interop.catz.asyncInstance
import zio.{Scope, ZIO}
import zio.test._

object GetFlowCheckpointsIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val partitioningDTO1: PartitioningDTO = Seq(
      PartitionDTO("stringA", "stringA"),
      PartitionDTO("stringB", "stringB")
    )

    suite("GetFlowCheckpointsIntegrationTests")(
      test("Returns expected sequence of flow of Checkpoints with existing partitioning") {
        val partitioningQueryDTO: CheckpointQueryDTO = CheckpointQueryDTO(
          partitioning = partitioningDTO1,
          limit = Some(10),
          checkpointName = Some("checkpointName")
        )

        for {
          getFlowCheckpoints <- ZIO.service[GetFlowCheckpoints]
          result <- getFlowCheckpoints(partitioningQueryDTO)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      }
    ).provide(
      GetFlowCheckpoints.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }

}

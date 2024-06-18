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

import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}
import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.fadb.exceptions.DataNotFoundException
import za.co.absa.fadb.status.FunctionStatus
import zio._
import zio.test._

import java.time.ZonedDateTime
import java.util.UUID

object WriteCheckpointIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("WriteCheckpointSuite")(
      test("Returns expected Left with DataNotFoundException as related partitioning is not in the database") {
        val checkpointDTO = CheckpointDTO(
          id = UUID.randomUUID(),
          name = "name",
          author = "author",
          partitioning = Seq(PartitionDTO("key1", "val1"), PartitionDTO("key2", "val2")),
          processStartTime = ZonedDateTime.now(),
          processEndTime = None,
          measurements =
            Set(MeasurementDTO(MeasureDTO("count", Seq("*")), MeasureResultDTO(TypedValue("1", ResultValueType.Long))))
        )
        for {
          writeCheckpoint <- ZIO.service[WriteCheckpoint]
          result <- writeCheckpoint(checkpointDTO)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      }
    ).provide(
      WriteCheckpoint.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }

}

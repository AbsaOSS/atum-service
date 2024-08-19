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

import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.{CheckpointDTO, MeasureDTO, MeasureResultDTO, MeasurementDTO, PartitionDTO}
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.db.fadb.exceptions.{DataConflictException, DataNotFoundException}
import za.co.absa.db.fadb.status.FunctionStatus
import zio._
import zio.interop.catz.asyncInstance
import zio.test._

import java.time.ZonedDateTime
import java.util.UUID

object WriteCheckpointV1IntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("WriteCheckpointSuite")(
      test("Returns expected Left with DataNotFoundException as related partitioning is not in the database") {
        val checkpointDTO = CheckpointDTO(
          id = UUID.randomUUID(),
          name = "name",
          author = "author",
          partitioning = Seq(PartitionDTO("key4", "value4")),
          processStartTime = ZonedDateTime.now(),
          processEndTime = Option(ZonedDateTime.now()),
          measurements = Set(
            MeasurementDTO(MeasureDTO("count", Seq("*")), MeasureResultDTO(TypedValue("1", ResultValueType.LongValue)))
          )
        )
        for {
          writeCheckpoint <- ZIO.service[WriteCheckpointV1]
          result <- writeCheckpoint(checkpointDTO)
        } yield assertTrue(result == Left(DataConflictException(FunctionStatus(32, "Partitioning not found"))))
      }
    ).provide(
      WriteCheckpointV1.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }

}

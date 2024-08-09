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

import doobie.implicits.toSqlInterpolator
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.model.{CheckpointItemFromDB, GetCheckpointV2Args}
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio._
import za.co.absa.atum.server.api.database.DoobieImplicits.Sequence.get
import doobie.postgres.implicits._
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstRowStatusAggregator

class GetCheckpointV2(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieMultipleResultFunctionWithAggStatus[GetCheckpointV2Args, Option[CheckpointItemFromDB], Task](input =>
      Seq(
        fr"${input.partitioningId}",
        fr"${input.checkpointId}"
      )
    )
    with StandardStatusHandling with ByFirstRowStatusAggregator {

  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq(
    "id_checkpoint",
    "checkpoint_name",
    "author",
    "measured_by_atum_agent",
    "measure_name",
    "measured_columns",
    "measurement_value",
    "checkpoint_start_time",
    "checkpoint_end_time"
  )
}

object GetCheckpointV2 {
  val layer: URLayer[PostgresDatabaseProvider, GetCheckpointV2] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetCheckpointV2()(Runs, dbProvider.dbEngine)
  }
}

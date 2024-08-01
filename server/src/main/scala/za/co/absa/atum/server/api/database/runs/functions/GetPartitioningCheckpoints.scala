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
import za.co.absa.atum.model.dto.CheckpointQueryDTO
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.model.{CheckpointFromDB, PartitioningForDB}
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import zio._
import io.circe.syntax.EncoderOps
import za.co.absa.atum.server.api.database.DoobieImplicits.Sequence.get
import doobie.postgres.implicits._
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.{jsonbGet, jsonbPut}
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstErrorStatusAggregator
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling

class GetPartitioningCheckpoints (implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
  extends DoobieMultipleResultFunctionWithAggStatus[CheckpointQueryDTO, CheckpointFromDB, Task] (
      values => Seq(
        fr"${PartitioningForDB.fromSeqPartitionDTO(values.partitioning).asJson}",
        fr"${values.limit}",
        fr"${values.checkpointName}"
      )
    ) with StandardStatusHandling with ByFirstErrorStatusAggregator {
  override val fieldsToSelect: Seq[String] = Seq(
    "status",
    "status_text",
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

object GetPartitioningCheckpoints {
  val layer: URLayer[PostgresDatabaseProvider, GetPartitioningCheckpoints] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetPartitioningCheckpoints()(Runs, dbProvider.dbEngine)
  }
}

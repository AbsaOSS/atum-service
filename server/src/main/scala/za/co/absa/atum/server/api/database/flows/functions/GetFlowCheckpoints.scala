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

import doobie.implicits.toSqlInterpolator
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.flows.Flows
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints.GetFlowCheckpointsArgs
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstErrorStatusAggregator
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio._
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet
import za.co.absa.atum.server.api.database.DoobieImplicits.Sequence.get
import doobie.postgres.implicits._
import za.co.absa.atum.server.model.database.CheckpointItemWithPartitioningFromDB

class GetFlowCheckpoints(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieMultipleResultFunctionWithAggStatus[GetFlowCheckpointsArgs, Option[
      CheckpointItemWithPartitioningFromDB
    ], Task](input =>
      Seq(
        fr"${input.flowId}",
        fr"${input.limit}",
        fr"${input.offset}",
        fr"${input.checkpointName}"
      )
    )
    with StandardStatusHandling
    with ByFirstErrorStatusAggregator {

  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq(
    "id_checkpoint",
    "checkpoint_name",
    "checkpoint_author",
    "measured_by_atum_agent",
    "measure_name",
    "measured_columns",
    "measurement_value",
    "checkpoint_start_time",
    "checkpoint_end_time",
    "id_partitioning",
    "partitioning",
    "partitioning_author",
    "has_more"
  )
}

object GetFlowCheckpoints {
  case class GetFlowCheckpointsArgs(
    flowId: Long,
    limit: Option[Int],
    offset: Option[Long],
    checkpointName: Option[String]
  )

  val layer: URLayer[PostgresDatabaseProvider, GetFlowCheckpoints] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetFlowCheckpoints()(Flows, dbProvider.dbEngine)
  }
}

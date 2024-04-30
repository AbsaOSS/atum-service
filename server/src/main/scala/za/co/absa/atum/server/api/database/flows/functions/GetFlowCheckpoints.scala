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

import doobie.Fragment
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import play.api.libs.json.Json
import za.co.absa.atum.model.dto.{CheckpointQueryDTO, CheckpointQueryResultDTO}
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.flows.Flows
import za.co.absa.atum.server.model.PartitioningForDB
import za.co.absa.fadb.DBSchema
import za.co.absa.fadb.doobie.DoobieEngine
import za.co.absa.fadb.doobie.DoobieFunction.DoobieMultipleResultFunction
import zio._
import zio.interop.catz._
//import doobie.postgres._
import doobie.postgres.implicits._
import za.co.absa.atum.server.api.database.DoobieImplicits.Sequence.get

class GetFlowCheckpoints(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieMultipleResultFunction[CheckpointQueryDTO, CheckpointQueryResultDTO, Task] {

  override val fieldsToSelect: Seq[String] = Seq(
    "id_checkpoint",
    "checkpoint_name",
    "measure_name", "measure_columns", "measurement_value",
    "checkpoint_start_time", "checkpoint_end_time",
  )

  override def sql(values: CheckpointQueryDTO)(implicit read: Read[CheckpointQueryResultDTO]): Fragment = {
    val partitioning = PartitioningForDB.fromSeqPartitionDTO(values.partitioning)
    val partitioningNormalized = Json.toJson(partitioning).toString

    sql"""SELECT ${Fragment.const(selectEntry)}
          FROM ${Fragment.const(functionName)}(
                  ${
                    import za.co.absa.atum.server.api.database.DoobieImplicits.Jsonb.jsonbPutUsingString
                    partitioningNormalized
                  },
                  ${values.limit},
                  ${values.checkpointName},
                ) AS ${Fragment.const(alias)};"""
  }

}

object GetFlowCheckpoints {
  val layer: URLayer[PostgresDatabaseProvider, GetFlowCheckpoints] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetFlowCheckpoints()(Flows, dbProvider.dbEngine)
  }
}

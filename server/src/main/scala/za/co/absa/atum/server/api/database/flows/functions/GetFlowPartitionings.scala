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
import io.circe.{DecodingFailure, Json}
import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningWithIdDTO}
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.flows.Flows
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings._
import za.co.absa.atum.server.model.database.PartitioningForDB
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstErrorStatusAggregator
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio.{Task, URLayer, ZIO, ZLayer}
import za.co.absa.atum.server.model.PartitioningResult
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet

class GetFlowPartitionings(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieMultipleResultFunctionWithAggStatus[GetFlowPartitioningsArgs, Option[
      GetFlowPartitioningsResult
    ], Task](args =>
      Seq(
        fr"${args.flowId}",
        fr"${args.limit}",
        fr"${args.offset}"
      )
    )
    with StandardStatusHandling
    with ByFirstErrorStatusAggregator {

  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq("id", "partitioning", "author", "has_more")
}

object GetFlowPartitionings {
  case class GetFlowPartitioningsArgs(flowId: Long, limit: Option[Int], offset: Option[Long])
  case class GetFlowPartitioningsResult(id: Long, partitioningJson: Json, author: String, hasMore: Boolean)
    extends PartitioningResult(id, partitioningJson, author)

  val layer: URLayer[PostgresDatabaseProvider, GetFlowPartitionings] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetFlowPartitionings()(Flows, dbProvider.dbEngine)
  }
}

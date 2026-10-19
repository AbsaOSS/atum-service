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
import doobie.postgres.implicits._
import io.circe.Json
import io.circe.syntax._
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.api.database.runs.functions.SearchPartitionings._
import za.co.absa.atum.server.model.PartitioningResult
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.{jsonbGet, jsonbPut}
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstErrorStatusAggregator
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio.{Task, URLayer, ZIO, ZLayer}

import java.time.ZonedDateTime

class SearchPartitionings(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieMultipleResultFunctionWithAggStatus[SearchPartitioningsArgs, Option[
      SearchPartitioningsResult
    ], Task](args =>
      Seq(
        fr"${args.limit}",
        fr"${args.offset}",
        fr"${args.keys.map(_.asJson)}",
        fr"${args.from}",
        fr"${args.to}"
      )
    )
    with StandardStatusHandling
    with ByFirstErrorStatusAggregator {

  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq("id", "partitioning", "author", "has_more")
}

object SearchPartitionings {
  case class SearchPartitioningsArgs(
    limit: Int,
    offset: Long,
    keys: Option[Map[String, String]],
    from: Option[ZonedDateTime],
    to: Option[ZonedDateTime]
  )

  case class SearchPartitioningsResult(id: Long, partitioningJson: Json, author: String, hasMore: Boolean)
      extends PartitioningResult(id, partitioningJson, author)

  val layer: URLayer[PostgresDatabaseProvider, SearchPartitionings] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new SearchPartitionings()(Runs, dbProvider.dbEngine)
  }
}

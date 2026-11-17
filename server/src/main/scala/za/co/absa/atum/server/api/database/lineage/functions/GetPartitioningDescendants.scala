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

package za.co.absa.atum.server.api.database.lineage.functions

import doobie.implicits.toSqlInterpolator
import io.circe.Json
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.lineage.Lineage
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningDescendants._
import za.co.absa.atum.server.model.{HasMore, PartitioningResult}
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet
import zio.{Task, URLayer, ZIO, ZLayer}

class GetPartitioningDescendants(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends OptionalRowMultipleResultFunction[GetPartitioningDescendantsArgs, GetPartitioningDescendantsResult](args =>
      Seq(
        fr"${args.partitioningId}",
        fr"${args.limit}",
        fr"${args.offset}"
      )
    ) {

  override def fieldsToSelect: Seq[String] =
    super.fieldsToSelect ++ Seq("descendant_id", "partitioning", "author", "has_more")
}

object GetPartitioningDescendants {
  case class GetPartitioningDescendantsArgs(partitioningId: Long, limit: Int, offset: Long)
  case class GetPartitioningDescendantsResult(id: Long, partitioningJson: Json, author: String, hasMore: Boolean)
      extends PartitioningResult(id, partitioningJson, author)
      with HasMore

  val layer: URLayer[PostgresDatabaseProvider, GetPartitioningDescendants] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetPartitioningDescendants()(Lineage, dbProvider.dbEngine)
  }
}

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
import doobie.postgres.implicits._
import io.circe.{Decoder, DecodingFailure, Json}
import za.co.absa.atum.model.dto.ChildPartitioningDTO
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.lineage.Lineage
import za.co.absa.atum.server.api.database.lineage.functions.GetDirectChildren._
import za.co.absa.atum.server.implicits.SeqImplicits.SeqEnhancements
import za.co.absa.atum.server.model.PartitioningResult
import za.co.absa.atum.server.model.database.PartitioningForDB
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstErrorStatusAggregator
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio.{Task, URLayer, ZIO, ZLayer}
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet

import java.time.ZonedDateTime

// Direct (immediate) children of a partitioning, from lineage.partitioning_edges.
// Migrated from flows.get_direct_children to lineage.get_direct_children in v0.11.0
class GetDirectChildren(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieMultipleResultFunctionWithAggStatus[Long, Option[GetDirectChildrenResult], Task](partitioningId =>
      Seq(fr"$partitioningId")
    )
    with StandardStatusHandling
    with ByFirstErrorStatusAggregator {

  override def fieldsToSelect: Seq[String] =
    super.fieldsToSelect ++ Seq(
      "child_id",
      "child_partitioning",
      "child_author",
      "edge_created_by",
      "edge_created_at"
    )
}

object GetDirectChildren {
  case class GetDirectChildrenResult(
    id: Long,
    partitioningJson: Json,
    author: String,
    linkedBy: String,
    linkedAt: ZonedDateTime
  ) extends PartitioningResult(id, partitioningJson, author) {

    def toChildPartitioningDTO(implicit
      decoder: Decoder[PartitioningForDB]
    ): Either[DecodingFailure, ChildPartitioningDTO] =
      toPartitioningWithIdDTO.map { p =>
        ChildPartitioningDTO(p.id, p.partitioning, p.author, Some(linkedBy), Some(linkedAt))
      }
  }

  object GetDirectChildrenResult {
    def resultsToChildPartitioningDTOs(
      results: Seq[GetDirectChildrenResult]
    ): Either[DecodingFailure, Seq[ChildPartitioningDTO]] =
      results.seqDecode(_.toChildPartitioningDTO)
  }

  val layer: URLayer[PostgresDatabaseProvider, GetDirectChildren] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetDirectChildren()(Lineage, dbProvider.dbEngine)
  }
}

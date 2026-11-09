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
import doobie.postgres.implicits._
import io.circe.{Decoder, DecodingFailure, Json}
import za.co.absa.atum.model.dto.ParentPartitioningDTO
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.flows.Flows
import za.co.absa.atum.server.api.database.flows.functions.GetDirectParents._
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

// Direct (immediate) parents of a partitioning, from flows.partitioning_edges. Mirrors GetFlowPartitionings:
// the DB function returns zero rows when the partitioning exists but has no parents (-> empty Seq, success),
// and a single status-41 row when the partitioning does not exist (-> StatusException -> NotFound in the
// repository layer). No pagination: direct parents are few and the DB function returns them all.
class GetDirectParents(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieMultipleResultFunctionWithAggStatus[Long, Option[GetDirectParentsResult], Task](
      partitioningId => Seq(fr"$partitioningId")
    )
    with StandardStatusHandling
    with ByFirstErrorStatusAggregator {

  override def fieldsToSelect: Seq[String] =
    super.fieldsToSelect ++ Seq(
      "parent_id",
      "parent_partitioning",
      "parent_author",
      "edge_created_by",
      "edge_created_at"
    )
}

object GetDirectParents {
  case class GetDirectParentsResult(
    id: Long,
    partitioningJson: Json,
    author: String,
    linkedBy: String,
    linkedAt: ZonedDateTime
  ) extends PartitioningResult(id, partitioningJson, author) {

    def toParentPartitioningDTO(implicit
      decoder: Decoder[PartitioningForDB]
    ): Either[DecodingFailure, ParentPartitioningDTO] =
      toPartitioningWithIdDTO.map { p =>
        ParentPartitioningDTO(p.id, p.partitioning, p.author, Some(linkedBy), Some(linkedAt))
      }
  }

  object GetDirectParentsResult {
    def resultsToParentPartitioningDTOs(
      results: Seq[GetDirectParentsResult]
    ): Either[DecodingFailure, Seq[ParentPartitioningDTO]] =
      results.seqDecode(_.toParentPartitioningDTO)
  }

  val layer: URLayer[PostgresDatabaseProvider, GetDirectParents] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetDirectParents()(Flows, dbProvider.dbEngine)
  }
}

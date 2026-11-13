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
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Json}
import za.co.absa.atum.model.dto.{LineageEdgeDTO, LineageGraphDTO, PartitionDTO, PartitioningWithIdDTO}
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.lineage.Lineage
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningLineageGraph._
import za.co.absa.atum.server.model.database.PartitioningForDB
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio._

// Returns the complete lineage graph (nodes + edges) for a partitioning.
// Migrated from flows.get_partitioning_lineage_graph to lineage.get_partitioning_lineage_graph in v0.11.0
class GetPartitioningLineageGraph(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieSingleResultFunctionWithStatus[GetPartitioningLineageGraphArgs, Option[LineageGraphResult], Task](
      args =>
        Seq(
          fr"${args.partitioningId}",
          fr"${args.direction}",
          fr"${args.depth}"
        )
    )
    with StandardStatusHandling {

  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq("lineage_graph")
}

object GetPartitioningLineageGraph {
  case class GetPartitioningLineageGraphArgs(
    partitioningId: Long,
    direction: String,
    depth: Int
  )

  private case class LineageNodeFromDB(
    id: Long,
    partitioning: Json,
    author: String
  )

  private object LineageNodeFromDB {
    implicit val decoder: Decoder[LineageNodeFromDB] = deriveDecoder
  }

  private case class LineageGraphFromDB(
    primaryPartitioningId: Long,
    depth: Int,
    direction: String,
    nodes: Seq[LineageNodeFromDB],
    edges: Seq[LineageEdgeDTO]
  )

  private object LineageGraphFromDB {
    implicit val decoder: Decoder[LineageGraphFromDB] = deriveDecoder
  }

  case class LineageGraphResult(lineageGraph: Json) {
    def toLineageGraphDTO: Either[io.circe.Error, LineageGraphDTO] = {
      lineageGraph.as[LineageGraphFromDB].flatMap { raw =>
        val decodedNodesOrError =
          raw.nodes.foldLeft[Either[io.circe.Error, Vector[PartitioningWithIdDTO]]](Right(Vector.empty)) {
            case (Right(acc), node) =>
              node.partitioning.as[PartitioningForDB].map { pdb =>
                val dto = pdb.keys.map(k => PartitionDTO(k, pdb.keysToValuesMap.getOrElse(k, "")))
                acc :+ PartitioningWithIdDTO(node.id, dto, node.author)
              }
            case (Left(err), _) => Left(err)
          }
        decodedNodesOrError.map { nodes =>
          LineageGraphDTO(raw.primaryPartitioningId, raw.depth, raw.direction, nodes, raw.edges)
        }
      }
    }
  }

  val layer: URLayer[PostgresDatabaseProvider, GetPartitioningLineageGraph] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetPartitioningLineageGraph()(Lineage, dbProvider.dbEngine)
  }
}

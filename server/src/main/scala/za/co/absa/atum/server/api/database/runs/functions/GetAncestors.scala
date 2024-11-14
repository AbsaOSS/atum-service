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
import io.circe.{DecodingFailure, Json}
import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningWithIdDTO}
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.api.database.runs.functions.GetAncestors._
import za.co.absa.atum.server.model.PartitioningForDB
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstErrorStatusAggregator
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio.{Task, URLayer, ZIO, ZLayer}

import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet

import scala.annotation.tailrec

class GetAncestors(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
  extends DoobieMultipleResultFunctionWithAggStatus[GetAncestorsArgs, Option[
    GetAncestorsResult
  ], Task](args =>
    Seq(
      fr"${args.partitioningId}",
      fr"${args.limit}",
      fr"${args.offset}"
    )
  )
    with StandardStatusHandling
    with ByFirstErrorStatusAggregator {

  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq("ancestor_id", "partitioning", "author", "has_more")
}

object GetAncestors {
  case class GetAncestorsArgs(partitioningId: Long, limit: Option[Int], offset: Option[Long])
  case class GetAncestorsResult(ancestor_id: Long, partitioningJson: Json, author: String, hasMore: Boolean)

  object GetAncestorsResult {

    @tailrec def resultsToPartitioningWithIdDTOs(
                                                  results: Seq[GetAncestorsResult],
                                                  acc: Seq[PartitioningWithIdDTO]
                                                ): Either[DecodingFailure, Seq[PartitioningWithIdDTO]] = {
      if (results.isEmpty) Right(acc)
      else {
        val head = results.head
        val tail = results.tail
        val decodingResult = head.partitioningJson.as[PartitioningForDB]
        decodingResult match {
          case Left(decodingFailure) => Left(decodingFailure)
          case Right(partitioningForDB) =>
            val partitioningDTO = partitioningForDB.keys.map { key =>
              PartitionDTO(key, partitioningForDB.keysToValuesMap(key))
            }
            resultsToPartitioningWithIdDTOs(tail, acc :+ PartitioningWithIdDTO(head.ancestor_id, partitioningDTO, head.author))
        }
      }
    }

  }

  val layer: URLayer[PostgresDatabaseProvider, GetAncestors] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetAncestors()(Runs, dbProvider.dbEngine)
  }
}

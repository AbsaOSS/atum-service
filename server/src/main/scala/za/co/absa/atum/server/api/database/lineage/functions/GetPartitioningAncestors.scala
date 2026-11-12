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

import cats.MonadError
import doobie.implicits.toSqlInterpolator
import io.circe.Json
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.lineage.Lineage
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningAncestors._
import za.co.absa.atum.server.model.PartitioningResult
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.{DoobieEngine, DoobieQueryWithStatus}
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.exceptions.StatusException
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstErrorStatusAggregator
import za.co.absa.db.fadb.status.{FailedOrRow, FunctionStatus, Row}
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio.{Task, URLayer, ZIO, ZLayer}
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet

// Returns the ancestors of a partitioning using the transitive closure (flows.partitioning_to_flow).
// Migrated from runs.get_partitioning_ancestors to lineage.get_partitioning_ancestors in v0.11.0
class GetPartitioningAncestors(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieMultipleResultFunctionWithAggStatus[GetPartitioningAncestorsArgs, Option[
      GetPartitioningAncestorsResult
    ], Task](args =>
      Seq(
        fr"${args.partitioningId}",
        fr"${args.limit}",
        fr"${args.offset}"
      )
    )
    with StandardStatusHandling
    with ByFirstErrorStatusAggregator {

  override def fieldsToSelect: Seq[String] =
    super.fieldsToSelect ++ Seq("ancestor_id", "partitioning", "author", "has_more")

  override protected def query(values: GetPartitioningAncestorsArgs)(implicit
    me: MonadError[Task, Throwable]
  ): Task[DoobieQueryWithStatus[Option[GetPartitioningAncestorsResult]]] =
    super.query(values).map { defaultQuery =>
      new DoobieQueryWithStatus[Option[GetPartitioningAncestorsResult]](defaultQuery.fragment, checkStatus)(
        defaultQuery.readStatusWithData
      ) {
        override def toStatusExceptionOrData(
          statusWithData: Row[Option[Option[GetPartitioningAncestorsResult]]]
        ): FailedOrRow[Option[GetPartitioningAncestorsResult]] =
          statusExceptionOrRow(checkStatus, statusWithData)
      }
    }
}

object GetPartitioningAncestors {
  case class GetPartitioningAncestorsArgs(partitioningId: Long, limit: Int, offset: Long)
  case class GetPartitioningAncestorsResult(id: Long, partitioningJson: Json, author: String, hasMore: Boolean)
      extends PartitioningResult(id, partitioningJson, author)

  /**
   *  Status check first (a non-OK status such as 41 becomes a `StatusException`), then the row; an OK row without
   *  data (status 14, no ancestors) becomes `None` instead of failing.
   */
  private[database] def statusExceptionOrRow(
    checkStatus: FunctionStatus => Option[StatusException],
    statusWithData: Row[Option[Option[GetPartitioningAncestorsResult]]]
  ): FailedOrRow[Option[GetPartitioningAncestorsResult]] =
    checkStatus(statusWithData.functionStatus).toLeft(
      Row(statusWithData.functionStatus, statusWithData.data.flatten)
    )

  val layer: URLayer[PostgresDatabaseProvider, GetPartitioningAncestors] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetPartitioningAncestors()(Lineage, dbProvider.dbEngine)
  }
}

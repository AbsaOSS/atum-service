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

import cats.MonadError
import doobie.implicits.toSqlInterpolator
import io.circe.Json
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningAncestors._
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

/**
 *  Wrapper of `runs.get_partitioning_ancestors(BIGINT, INT, BIGINT)`.
 *  Statuses: 10 OK (one row per ancestor), 14 OK with NO ancestors (a single row whose data columns are all NULL),
 *  41 partitioning not found (mapped to a not-found exception by `StandardStatusHandling`).
 *
 *  fa-db 0.7.0 `DoobieQueryWithStatus` throws `IllegalStateException("Status is OK but data is missing")` for an OK
 *  row without data, which surfaced as HTTP 500 for an existing partitioning that has no ancestors. The query is
 *  therefore replaced by one that treats an OK status without data as "no ancestor" (`None`), so the call yields an
 *  empty result; the 41 row is still turned into a `DataNotFoundException` before any data is looked at.
 */
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
  private[functions] def statusExceptionOrRow(
    checkStatus: FunctionStatus => Option[StatusException],
    statusWithData: Row[Option[Option[GetPartitioningAncestorsResult]]]
  ): FailedOrRow[Option[GetPartitioningAncestorsResult]] =
    checkStatus(statusWithData.functionStatus).toLeft(
      Row(statusWithData.functionStatus, statusWithData.data.flatten)
    )

  val layer: URLayer[PostgresDatabaseProvider, GetPartitioningAncestors] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetPartitioningAncestors()(Runs, dbProvider.dbEngine)
  }
}

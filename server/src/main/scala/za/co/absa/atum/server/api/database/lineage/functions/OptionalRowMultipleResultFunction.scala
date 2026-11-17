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
import doobie.{Fragment, Read}
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.doobie.{DoobieEngine, DoobieQueryWithStatus}
import za.co.absa.db.fadb.exceptions.StatusException
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstErrorStatusAggregator
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import za.co.absa.db.fadb.status.{FailedOrRow, FunctionStatus, Row}
import zio.Task

/**
 *  Shared base for lineage multiple-result DB functions whose OK status-14 row carries all-NULL data columns.
 */
private[functions] abstract class OptionalRowMultipleResultFunction[A, R](
  sqlArgs: A => Seq[Fragment]
)(implicit
  schema: DBSchema,
  dbEngine: DoobieEngine[Task],
  read: Read[Option[R]]
) extends DoobieMultipleResultFunctionWithAggStatus[A, Option[R], Task](sqlArgs)
    with StandardStatusHandling
    with ByFirstErrorStatusAggregator {

  override protected def query(values: A)(implicit
    me: MonadError[Task, Throwable]
  ): Task[DoobieQueryWithStatus[Option[R]]] =
    super.query(values).map { defaultQuery =>
      new DoobieQueryWithStatus[Option[R]](defaultQuery.fragment, checkStatus)(
        defaultQuery.readStatusWithData
      ) {
        override def toStatusExceptionOrData(
          statusWithData: Row[Option[Option[R]]]
        ): FailedOrRow[Option[R]] =
          OptionalRowMultipleResultFunction.statusExceptionOrRow(checkStatus, statusWithData)
      }
    }
}

private[database] object OptionalRowMultipleResultFunction {

  /**
   *  Status check first (a non-OK status such as 41 becomes a `StatusException`), then the row; an OK row without
   *  data (status 14, empty result set) becomes `None` instead of failing.
   */
  def statusExceptionOrRow[R](
    checkStatus: FunctionStatus => Option[StatusException],
    statusWithData: Row[Option[Option[R]]]
  ): FailedOrRow[Option[R]] =
    checkStatus(statusWithData.functionStatus).toLeft(
      Row(statusWithData.functionStatus, statusWithData.data.flatten)
    )
}

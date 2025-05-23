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

package za.co.absa.atum.server.api.common.repository

import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.db.fadb.exceptions._
import za.co.absa.db.fadb.status.{FailedOrRow, FailedOrRows}
import zio._

trait BaseRepository {

  private def logAndReturn[R](
    operationName: String,
    dbFuncCall: Task[Either[StatusException, R]]
  ): ZIO[Any, Throwable, Either[StatusException, R]] = {
    dbFuncCall
      .tap {
        case Left(statusException: StatusException) =>
          ZIO.logError(
            s"Exception caused by operation: '$operationName': " +
              s"(${statusException.status.statusCode}) ${statusException.status.statusText}"
          )
        case Right(_) => ZIO.logDebug(s"Operation '$operationName' succeeded in database")
      }
  }

  private def defaultErrorHandler(operationName: String): PartialFunction[Throwable, DatabaseError] = {
    case statusException: StatusException =>
      val message = s"Exception caused by operation: '$operationName': " +
        s"(${statusException.status.statusCode}) ${statusException.status.statusText}"

      statusException match {
        case DataConflictException(_) => ConflictDatabaseError(message)
        case DataNotFoundException(_) => NotFoundDatabaseError(message)
        case ErrorInDataException(_) => ErrorInDataDatabaseError(message)
        case _ => GeneralDatabaseError(message)
      }
    case error =>
      GeneralDatabaseError(s"Operation '$operationName' failed with unexpected error: ${error.getMessage}")
  }

  protected def dbSingleResultCallWithStatus[R](
    dbFuncCall: Task[FailedOrRow[R]],
    operationName: String
  ): IO[DatabaseError, R] = {
    logAndReturn(operationName, dbFuncCall)
      .flatMap {
        case Left(statusException) => ZIO.fail(statusException)
        case Right(value) => ZIO.succeed(value.data)
      }
      .mapError {
        defaultErrorHandler(operationName)
      }
      .tapError(error => ZIO.logError(s"Operation '$operationName' failed: ${error.message}"))
  }

  protected def dbMultipleResultCallWithAggregatedStatus[R](
    dbFuncCall: Task[FailedOrRows[R]],
    operationName: String
  ): IO[DatabaseError, Seq[R]] = {
    logAndReturn(operationName, dbFuncCall)
      .flatMap {
        case Left(statusException) => ZIO.fail(statusException)
        case Right(value) => ZIO.succeed(value.map(_.data))
      }
      .mapError {
        defaultErrorHandler(operationName)
      }
      .tapError(error => ZIO.logError(s"Operation '$operationName' failed: ${error.message}"))
  }

}

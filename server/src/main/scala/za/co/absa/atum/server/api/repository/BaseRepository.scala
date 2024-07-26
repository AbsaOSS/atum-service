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

package za.co.absa.atum.server.api.repository

import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.db.fadb.exceptions.StatusException
import za.co.absa.db.fadb.status.{FailedOrRow, FailedOrRows}
import zio._

// R - dbSingleResultCall[R] => IO[DatabaseError, R]
// Seq[R] dbMultipleResultCall => IO[DatabaseError, Seq[R]]
// FailedOrRow[R] ~ Either[StatusException, Row[R]] - dbSingleResultCallWithStatus => IO[DatabaseError, R]
// Seq[FailedOrRow[R]] ~ Seq[Either[StatusException, Row[R]]] - dbMultipleResultCallWithStatus => IO[DatabaseError, Seq[R]]
// FailedOrRows[R] ~ Either[StatusException, Seq[Row[R]]] - dbMultipleResultCallWithAggregatedStatus => IO[DatabaseError, Seq[R]]

sealed trait PaginatedResult[R]
case class ResultHasMore[R](data: Seq[R]) extends PaginatedResult[R]
case class ResultNoMore[R](data: Seq[R]) extends PaginatedResult[R]

trait BaseRepository {

  private def logOperationResult[R](operationName: String, dbFuncCall: Task[R]):
  ZIO[Any, Throwable, R] = {
    dbFuncCall
      .tap {
      case Left(statusException: StatusException) =>
        ZIO.logError(
          s"Exception caused by operation: '$operationName': " +
            s"(${statusException.status.statusCode}), ${statusException.status.statusText}"
        )
      case Right(_) => ZIO.logDebug(s"Operation '$operationName' succeeded in database")
    }
  }

  private def defaultErrorHandler[R](operationName: String):
  PartialFunction[Throwable, DatabaseError] = {
    case statusException: StatusException =>
      DatabaseError(
        s"Exception caused by operation: '$operationName': " +
          s"(${statusException.status.statusCode}) ${statusException.status.statusText}"
      )
    case error =>
      DatabaseError(s"Operation '$operationName' failed with unexpected error: ${error.getMessage}")
  }

  private def dbCall[R](dbFuncCall: Task[R], operationName: String):
  IO[DatabaseError, R] = {
    dbFuncCall
      .zipLeft(ZIO.logDebug(s"Operation '$operationName' succeeded in database"))
      .mapError(error => DatabaseError(error.getMessage))
      .tapError(error => ZIO.logError(s"Operation '$operationName' failed: ${error.message}"))
  }

  def dbSingleResultCall[R](dbFuncCall: Task[R], operationName: String):
  IO[DatabaseError, R] = {
    dbCall(dbFuncCall, operationName)
  }

  def dbMultipleResultCall[R](dbFuncCall: Task[Seq[R]], operationName: String):
  IO[DatabaseError, Seq[R]] = {
    dbCall(dbFuncCall, operationName)
  }

  def dbSingleResultCallWithStatus[R](dbFuncCall: Task[FailedOrRow[R]], operationName: String):
  IO[DatabaseError, R] = {
    logOperationResult(operationName, dbFuncCall)
      .flatMap {
        case Left(statusException) => ZIO.fail(statusException)
        case Right(value) => ZIO.succeed(value.data)
      }
      .mapError {
        defaultErrorHandler(operationName)
      }
      .tapError(error => ZIO.logError(s"Operation '$operationName' failed: ${error.message}"))
  }

  def dbMultipleResultCallWithAggregatedStatus[R](dbFuncCall: Task[FailedOrRows[R]], operationName: String):
  IO[DatabaseError, Seq[R]] = {
    logOperationResult(operationName, dbFuncCall)
      .flatMap {
        case Left(statusException) => ZIO.fail(statusException)
        case Right(value) => ZIO.succeed(value.map(_.data))
      }
      .mapError {
        defaultErrorHandler(operationName)
      }
      .tapError(error => ZIO.logError(s"Operation '$operationName' failed: ${error.message}"))
  }

  def dbPaginatedCall[R](dbFuncCall: Task[FailedOrRows[R]], operationName: String):
  IO[DatabaseError, PaginatedResult[R]] = {
    logOperationResult(operationName, dbFuncCall)
      .flatMap {
        case Left(statusException) => ZIO.fail(statusException)
        case Right(value) => ZIO.succeed(
          if (value.nonEmpty && value.head.functionStatus.statusCode == 11){
            ResultHasMore(value.map(_.data))
          } else {
            ResultNoMore(value.map(_.data))
          }
        )
      }
      .mapError {
        defaultErrorHandler(operationName)
      }
      .tapError(error => ZIO.logError(s"Operation '$operationName' failed: ${error.message}"))
  }

}

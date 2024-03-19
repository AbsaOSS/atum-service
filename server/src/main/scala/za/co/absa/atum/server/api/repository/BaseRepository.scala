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
import za.co.absa.fadb.exceptions.StatusException
import zio._

trait BaseRepository {

  def dbCallWithStatus[R](
    dbFuncCall: Task[Either[StatusException, R]],
    operationName: String
  ): IO[DatabaseError, Either[StatusException, R]] = {
    dbFuncCall
      .tap {
        case Left(statusException) =>
          ZIO.logError(
            s"Exception caused by operation: '$operationName': " +
              s"(${statusException.status.statusCode}) ${statusException.status.statusText}"
          )
        case Right(_) => ZIO.logDebug(s"Operation '$operationName' succeeded in database")
      }
      .mapError(error => DatabaseError(error.getMessage))
      .tapError(error => ZIO.logError(s"Operation '$operationName' failed: ${error.message}"))
  }
}

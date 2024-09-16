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

package za.co.absa.atum.server.api.service

import za.co.absa.atum.server.api.exception.DatabaseError._
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.exception._
import zio._

trait BaseService {

  def repositoryCall[R](repositoryCall: IO[DatabaseError, R], operationName: String): IO[ServiceError, R] = {
    repositoryCall
      .mapError {
        case ConflictDatabaseError(message) => ConflictServiceError(createMessage(operationName, message))
        case NotFoundDatabaseError(message) => NotFoundServiceError(createMessage(operationName, message))
        case GeneralDatabaseError(message) => GeneralServiceError(createMessage(operationName, message))
        case ErrorInDataDatabaseError(message) => ErrorInDataServiceError(createMessage(operationName, message))
      }
  }

  private def createMessage(operationName: String, message: String): String = {
    s"Failed to perform '$operationName': $message"
  }

}

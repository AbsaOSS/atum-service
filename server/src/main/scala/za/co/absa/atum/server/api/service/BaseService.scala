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

import com.github.dwickern.macros.NameOf._
import za.co.absa.atum.server.api.exception.{DatabaseError, ServiceError}
import za.co.absa.fadb.exceptions.StatusException
import zio._

trait BaseService {

  def handleRepositoryCall[T](
    repositoryCall: T => IO[DatabaseError, Either[StatusException, Unit]],
    inputDTO: T
  ): IO[ServiceError, Either[StatusException, Unit]] = {

    val operationName = nameOf(repositoryCall)

    repositoryCall(inputDTO)
      .mapError { case DatabaseError(underlyingMessage) =>
        repositoryCall.toString()
        ServiceError(s"Failed to perform '$operationName': $underlyingMessage")
      }
  }

}
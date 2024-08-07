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

package za.co.absa.atum.server.api.controller

import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.model.{ErrorResponse, InternalServerErrorResponse}
import za.co.absa.atum.server.model.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import zio._

trait BaseController {

  def serviceCall[A, B](
    serviceCall: IO[ServiceError, A],
    onSuccessFnc: A => B
  ): IO[ErrorResponse, B] = {

    serviceCall
      .mapError { serviceError: ServiceError =>
        InternalServerErrorResponse(serviceError.message)
      }
      .flatMap { result =>
        ZIO.succeed(onSuccessFnc(result))
      }

  }

  protected def mapToSingleSuccessResponse[A](
    effect: IO[ErrorResponse, A]
  ): IO[ErrorResponse, SingleSuccessResponse[A]] = {
    effect.map(SingleSuccessResponse(_))
  }

  protected def mapToMultiSuccessResponse[A](
    effect: IO[ErrorResponse, Seq[A]]
  ): IO[ErrorResponse, MultiSuccessResponse[A]] = {
    effect.map(MultiSuccessResponse(_))
  }
}

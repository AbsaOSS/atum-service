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

import io.circe.{Decoder, parser}
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.http.ApiPaths
import za.co.absa.atum.server.model.SuccessResponse._
import za.co.absa.atum.server.model.PaginatedResult.{ResultHasMore, ResultNoMore}
import za.co.absa.atum.server.model._
import zio._

import java.util.Base64

trait BaseController {

  def serviceCall[A, B](
    serviceCall: IO[ServiceError, A],
    onSuccessFnc: A => B = identity[A] _
  ): IO[ErrorResponse, B] = {

    serviceCall
      .mapError {
        case ConflictServiceError(message) => ConflictErrorResponse(message)
        case NotFoundServiceError(message) => NotFoundErrorResponse(message)
        case GeneralServiceError(message) => InternalServerErrorResponse(message)
        case ErrorInDataServiceError(message) => ErrorInDataErrorResponse(message)
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

  protected def mapToPaginatedResponse[A](
    limit: Int,
    offset: Long,
    effect: IO[ErrorResponse, PaginatedResult[A]]
  ): IO[ErrorResponse, PaginatedResponse[A]] = {
    effect.map {
      case ResultHasMore(data) => PaginatedResponse(data, Pagination(limit, offset, hasMore = true))
      case ResultNoMore(data) => PaginatedResponse(data, Pagination(limit, offset, hasMore = false))
    }
  }

  // Root-anchored URL path
  // https://stackoverflow.com/questions/2005079/absolute-vs-relative-urls/78439286#78439286
  protected def createV2RootAnchoredResourcePath(parts: Seq[String]): IO[ErrorResponse, String] = {
    ZIO.succeed(s"/${ApiPaths.Api}/${ApiPaths.V2}/${parts.mkString("/")}")
  }

  protected def base64Decode[T: Decoder](base64EncodedString: String): Either[io.circe.Error, T] = {
    val decodedBytes = Base64.getDecoder.decode(base64EncodedString)
    val decodedString = new String(decodedBytes, "UTF-8")
    parser.decode[T](decodedString)
  }
}

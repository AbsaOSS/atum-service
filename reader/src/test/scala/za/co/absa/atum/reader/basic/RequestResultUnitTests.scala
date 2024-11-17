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

package za.co.absa.atum.reader.basic

import io.circe.ParsingFailure
import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.client3.{DeserializationException, HttpError, Response, ResponseException}
import sttp.model.StatusCode
import za.co.absa.atum.model.dto.PartitionDTO
import za.co.absa.atum.model.envelopes.NotFoundErrorResponse
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax
import za.co.absa.atum.reader.basic.RequestResult._

class RequestResultUnitTests extends AnyFunSuiteLike {
  test("Response.toRequestResult keeps the right value") {
    val partitionDTO = PartitionDTO("someKey", "someValue")
    val body = Right(partitionDTO)
    val source: Response[Either[ResponseException[String, CirceError], PartitionDTO]] = Response(
      body,
      StatusCode.Ok
    )
    val result = source.toRequestResult
    assert(result == body)
  }

  test("Response.toRequestResult keeps the left value if it's a CirceError") {
    val circeError: CirceError = ParsingFailure("Just a test error", new Exception)
    val deserializationException = DeserializationException("This is not a json", circeError)
    val body = Left(deserializationException)
    val source: Response[Either[ResponseException[String, CirceError], PartitionDTO]] = Response(
      body,
      StatusCode.Ok
    )
    val result = source.toRequestResult
    assert(result == body)
  }

  test("Response.toRequestResult decodes NotFound error") {
    val error = NotFoundErrorResponse("This is a test")
    val errorResponse = error.asJsonString
    val httpError = HttpError(errorResponse, StatusCode.NotFound)
    val source: Response[Either[ResponseException[String, CirceError], PartitionDTO]] = Response(
      Left(httpError),
      StatusCode.Ok
    )
    val result = source.toRequestResult
    val expected: RequestResult[PartitionDTO] = Left(HttpError(error, httpError.statusCode))
    assert(result == expected)
  }

  test("Response.toRequestResult fails to decode InternalServerErrorResponse error") {
    val responseBody = "This is not a json"
    val httpError = HttpError(responseBody, StatusCode.InternalServerError)
    val source: Response[Either[ResponseException[String, CirceError], PartitionDTO]] = Response(
      Left(httpError),
      StatusCode.Ok
    )
    val result = source.toRequestResult

    assert(result.isLeft)
    result.swap.foreach { e =>
      // investigate the error
      assert(e.isInstanceOf[DeserializationException[_]])
      val ce = e.asInstanceOf[DeserializationException[ParsingFailure]]
      assert(ce.body == responseBody)
    }
  }

}

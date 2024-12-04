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

package za.co.absa.atum.model.envelopes

import io.circe.ParsingFailure
import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax

import java.util.UUID

class ErrorResponseUnitTests extends AnyFunSuiteLike {
  test("ErrorResponse.basedOnStatusCode should return correct error response on `Bad Request`") {
    val originalError = BadRequestResponse("Bad Request", UUID.randomUUID())
    val errorResponse = ErrorResponse.basedOnStatusCode(400, originalError.asJsonString)
    assert(errorResponse == Right(originalError))
  }

  test("ErrorResponse.basedOnStatusCode should return correct error response on `Unauthorized`") {
    val originalError = UnauthorizedErrorResponse("Unauthorized", UUID.randomUUID())
    val errorResponse = ErrorResponse.basedOnStatusCode(401, originalError.asJsonString)
    assert(errorResponse == Right(originalError))
  }

  test("ErrorResponse.basedOnStatusCode should return correct error response on `Not Found`") {
    val originalError = NotFoundErrorResponse("Not Found", UUID.randomUUID())
    val errorResponse = ErrorResponse.basedOnStatusCode(404, originalError.asJsonString)
    assert(errorResponse == Right(originalError))
  }

  test("ErrorResponse.basedOnStatusCode should return correct error response on `Conflict`") {
    val originalError = ConflictErrorResponse("Conflict", UUID.randomUUID())
    val errorResponse = ErrorResponse.basedOnStatusCode(409, originalError.asJsonString)
    assert(errorResponse == Right(originalError))
  }

  test("ErrorResponse.basedOnStatusCode should return correct error response on `Internal Server Error`") {
    val originalError = InternalServerErrorResponse("Internal Server Error", UUID.randomUUID())
    val errorResponse = ErrorResponse.basedOnStatusCode(500, originalError.asJsonString)
    assert(errorResponse == Right(originalError))
  }

  test("ErrorResponse.basedOnStatusCode should return GeneralErrorResponse on unknown status code") {
    val originalError = GeneralErrorResponse("Heluva", UUID.randomUUID())
    val errorResponse = ErrorResponse.basedOnStatusCode(600, originalError.asJsonString)
    assert(errorResponse == Right(originalError))
  }

  test("ErrorResponse.basedOnStatusCode fails on invalid JSON") {
    val message = "This is not a JSON"
    val errorResponse = ErrorResponse.basedOnStatusCode(400, message)
    assert(errorResponse.isLeft)
    errorResponse.swap.foreach{e =>
      // investigate the error
      assert(e.isInstanceOf[ParsingFailure])
    }
  }

}

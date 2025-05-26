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

package za.co.absa.atum.server.api.common.http

import sttp.client3.circe.asJson
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.envelopes.StatusResponse
import zio.test.Assertion.equalTo
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Scope, ZIO}

object HealthEndpointUnitTests extends ZIOSpecDefault {

  private val healthServerEndpoint = Endpoints.healthEndpoint.zServerLogic((_: Unit) => ZIO.succeed(StatusResponse.up))

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val backendStub = TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[Any]))
      .whenServerEndpoint(healthServerEndpoint)
      .thenRunLogic()
      .backend()

    suite("HealthEndpointSuite")(
      test("Returns expected StatusResponse") {
        val request = basicRequest
          .get(uri"https://test.com/health")
          .response(asJson[StatusResponse])

        val response = request.send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)

        assertZIO(body <&> statusCode)(
          equalTo(Right(StatusResponse.up), StatusCode.Ok)
        )
      }
    )
  }
}

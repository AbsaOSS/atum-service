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

package za.co.absa.atum.reader.core

import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.capabilities
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Identity, SttpBackend}
import sttp.client3.monad.IdMonad
import sttp.monad.MonadError
import za.co.absa.atum.reader.core.RequestResult.{RequestFail, RequestOK, RequestResult}
import za.co.absa.atum.reader.exceptions.RequestException.ParsingException
import za.co.absa.atum.reader.server.ServerConfig

class ReaderUnitTests extends AnyFunSuiteLike {
  class ReaderForTesting[F[_]: MonadError](
                                            implicit serverConfig: ServerConfig,
                                            backend: SttpBackend[F, Any]
                                          )
    extends Reader[F]{

    override def mapRequestResultF[I, O](requestResult: RequestResult[I], f: I => F[RequestResult[O]]): F[RequestResult[O]] = {
      super.mapRequestResultF(requestResult, f)
    }
  }

  private implicit val serverConfig: ServerConfig = ServerConfig("http://localhost:8080")
  private implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
  private implicit val monad: MonadError[Identity] = IdMonad

  test("RequestResult should be mapped if Right") {
    def fnc(b: Int): Identity[RequestResult[String]] = Right(b.toString)
    val reader = new ReaderForTesting[Identity]
    val requestResult = RequestOK(1)
    val result = reader.mapRequestResultF(requestResult, fnc)
    assert(result == Right("1"))
  }

  test("RequestResult should no map if Left") {
    def fnc(b: Int): Identity[RequestResult[String]] = Right(b.toString)
    val reader = new ReaderForTesting[Identity]
    val requestResult = RequestFail(ParsingException("Just a test", ""))
    val result = reader.mapRequestResultF(requestResult, fnc)
    assert(result == requestResult)
  }
}

/*
 * Copyright 2024 ABSA Group Limited
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

package za.co.absa.atum.reader.server.zio

import sttp.client3.{Identity, RequestT}
import za.co.absa.atum.reader.server.GenericServerConnection.RequestResult
import zio.test.ZIOSpecDefault
import zio._
import zio.test._

object ZioServerConnectionUnitTests extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("ZioServerConnection")(
      test("close does nothing and succeeds") {
        val connection = new ZioServerConnection("foo.bar") {
          override protected def executeRequest[R](request: RequestT[Identity, RequestResult[R], Any]): Task[RequestResult[R]] = ???
        }
        val expected: Unit = ()
        for {
          result <- connection.close()
        } yield assertTrue(result == expected)
      }
    )
  }
}

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

package za.co.absa.atum.reader

import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.client3.SttpBackend
import sttp.client3.testing.SttpBackendStub
import za.co.absa.atum.model.types.basic.AtumPartitions
import za.co.absa.atum.reader.server.ServerConfig
import za.co.absa.atum.reader.implicits.future.futureMonadError

import scala.concurrent.Future

class FlowReaderUnitTests extends AnyFunSuiteLike {
  private implicit val serverConfig: ServerConfig = ServerConfig.fromConfig()

  test("mainFlowPartitioning is the same as partitioning") {
    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    implicit val server: SttpBackend[Future, Any] = SttpBackendStub.asynchronousFuture

    val result = new FlowReader(atumPartitions).mainFlowPartitioning
    assert(result == atumPartitions)
  }
}

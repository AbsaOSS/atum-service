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

package za.co.absa.atum.reader.server.io

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}
import sttp.client3.{Identity, RequestT, Response, SttpBackend}
import sttp.client3.armeria.cats.ArmeriaCatsBackend
import sttp.client3.impl.cats.CatsMonadAsyncError
import za.co.absa.atum.reader.server.GenericServerConnection
import za.co.absa.atum.reader.server.GenericServerConnection.RequestResult


class ArmeriaServerConnection protected(serverUrl: String, backend: SttpBackend[IO, Any], closeable: Boolean)
  extends GenericServerConnection[IO](serverUrl)(new CatsMonadAsyncError[IO]) {

  def this(mserverUrl: String) = {
    this(mserverUrl, ArmeriaCatsBackend[IO](), closeable = true)
  }

  def this(config: Config = ConfigFactory.load()) = {
    this(GenericServerConnection.atumServerUrl(config ), ArmeriaCatsBackend[IO](), closeable = true)
  }

  override protected def executeRequest[R](request: RequestT[Identity, RequestResult[R], Any]): IO[Response[RequestResult[R]]] = {
    request.send(backend)
  }

  override def close(): IO[Unit] = {
    if (closeable) {
      backend.close()
    } else {
      IO.unit
    }
  }

}

object ArmeriaServerConnection {
  lazy implicit val serverConnection: ArmeriaServerConnection = new ArmeriaServerConnection()

  def use[R](serverUrl: String)(fnc: ArmeriaServerConnection => IO[R]): IO[R] = {
    ArmeriaCatsBackend.resource[IO]().use{backend =>
      val serverConnection = new ArmeriaServerConnection(serverUrl, backend, false)
      fnc(serverConnection)
    }
  }
}

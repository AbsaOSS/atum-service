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

import com.typesafe.config.{Config, ConfigFactory}
import sttp.client3.{Identity, RequestT, Response}
import sttp.client3.armeria.zio.ArmeriaZioBackend
import za.co.absa.atum.reader.server.GenericServerConnection
import za.co.absa.atum.reader.server.GenericServerConnection.ReaderResponse
import zio.{Task, ZIO, _}


class ServerConnection(serverUrl: String)
  extends GenericServerConnection[Task](serverUrl) {

  def this(config: Config = ConfigFactory.load()) = {
    this(GenericServerConnection.atumServerUrl(config ))
  }

  override protected def executeRequest(request: RequestT[Identity, Either[String, String], Any]): Task[ReaderResponse] = {
    val x = ArmeriaZioBackend.usingDefaultClient().flatMap { backend =>
      val y: Task[Response[Either[String, String]]] = backend.send(request)
      y
    }
    x
  }
}

object ServerConnection {
  lazy implicit val serverConnection: ServerConnection = new ServerConnection()
}

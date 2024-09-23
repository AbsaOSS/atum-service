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

package za.co.absa.atum.reader.server.future

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.{ExecutionContext, Future}
import sttp.client3.{Identity, RequestT, Response}
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import za.co.absa.atum.reader.server.GenericServerConnection
import za.co.absa.atum.reader.server.GenericServerConnection.ReaderResponse


class ServerConnection(serverUrl: String)(implicit executor: ExecutionContext) extends GenericServerConnection[Future](serverUrl) {

  def this(config: Config = ConfigFactory.load())(implicit executor: ExecutionContext) = {
    this(GenericServerConnection.atumServerUrl(config ))(executor)
  }

  private val asyncHttpClientFutureBackend = AsyncHttpClientFutureBackend()

  override protected def executeRequest(request: RequestT[Identity, Either[String, String], Any]): Future[ReaderResponse] = {
    request.send(asyncHttpClientFutureBackend)
  }
}

object ServerConnection {
  lazy implicit val serverConnection: ServerConnection = new ServerConnection()(ExecutionContext.Implicits.global)
}

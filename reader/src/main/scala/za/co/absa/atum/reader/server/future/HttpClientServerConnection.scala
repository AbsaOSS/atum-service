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

package za.co.absa.atum.reader.server.future

// TODO #298 needs Java 11 cross-build
//import com.typesafe.config.{Config, ConfigFactory}
//import scala.concurrent.{ExecutionContext, Future}
//import sttp.client3.{HttpClientFutureBackend, SttpBackend}
//
//import za.co.absa.atum.reader.server.GenericServerConnection
//
//
//class HttpClientServerConnection private(serverUrl: String, closeable: Boolean)(implicit executor: ExecutionContext)
//  extends FutureServerConnection(serverUrl, closeable) {
//
//  def this(serverUrl: String)(implicit executor: ExecutionContext) = {
//    this(serverUrl, true)(executor)
//  }
//
//  def this(config: Config = ConfigFactory.load())(implicit executor: ExecutionContext) = {
//    this(GenericServerConnection.atumServerUrl(config))(executor)
//  }
//
//  override protected val backend: SttpBackend[Future, Any] = HttpClientFutureBackend()
//
//}
//
//object HttpClientServerConnection {
//  lazy implicit val serverConnection: FutureServerConnection = new HttpClientServerConnection()(ExecutionContext.Implicits.global)
//
//  def use[R](serverUrl: String)(fnc: HttpClientServerConnection => Future[R])
//            (implicit executor: ExecutionContext): Future[R] = {
//    val serverConnection = new HttpClientServerConnection(serverUrl)
//    try {
//      fnc(serverConnection)
//    } finally {
//      serverConnection.close()
//    }
//  }
//}

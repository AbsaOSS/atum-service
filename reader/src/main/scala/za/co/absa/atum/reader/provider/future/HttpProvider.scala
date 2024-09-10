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

package za.co.absa.atum.reader.provider.future

import cats.implicits.catsStdInstancesForFuture
import com.typesafe.config.{Config, ConfigFactory}
import sttp.client3.Response
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import za.co.absa.atum.reader.provider.AbstractHttpProvider

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


class HttpProvider(serverUrl: String)(implicit executor: ExecutionContext) extends AbstractHttpProvider[Future](serverUrl) {

  def this(config: Config = ConfigFactory.load())(implicit executor: ExecutionContext) = {
    this(AbstractHttpProvider.atumServerUrl(config ))(executor)
  }

  private val asyncHttpClientFutureBackend = AsyncHttpClientFutureBackend()

  override protected def executeRequest(requestFnc: RequestFunction): Future[Response[Either[String, String]]] = {
    requestFnc(asyncHttpClientFutureBackend)
  }

  override protected def mapResponse[R](
                                         response: Future[Response[Either[String, String]]],
                                         mapperFnc: ResponseMapperFunction[R]): Future[Try[R]] = {
    response.map(mapperFnc)
  }
}

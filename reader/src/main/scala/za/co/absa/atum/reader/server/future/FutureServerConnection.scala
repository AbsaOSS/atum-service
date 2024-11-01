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

import scala.concurrent.{ExecutionContext, Future}
import sttp.client3.{Identity, RequestT, SttpBackend}

import za.co.absa.atum.reader.server.GenericServerConnection
import za.co.absa.atum.reader.server.GenericServerConnection.RequestResult


abstract class FutureServerConnection(serverUrl: String, closeable: Boolean)(implicit executor: ExecutionContext)
  extends GenericServerConnection[Future](serverUrl) {

  protected val backend: SttpBackend[Future, Any]

  override protected def executeRequest[R](request: RequestT[Identity, RequestResult[R], Any]): Future[RequestResult[R]] = {
    request.send(backend).map(_.body)
  }

  override def close(): Future[Unit] = {
    if (closeable) {
      backend.close()
    } else {
      Future.successful(())
    }
  }

}


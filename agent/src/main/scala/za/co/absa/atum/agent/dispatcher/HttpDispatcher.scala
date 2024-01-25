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

package za.co.absa.atum.agent.dispatcher

import com.typesafe.config.Config
import org.apache.spark.internal.Logging
import sttp.client3._
import sttp.model.Uri
import za.co.absa.atum.agent.exception.AtumAgentException.HttpException
import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, AtumContextDTO, CheckpointDTO, PartitioningSubmitDTO}
import za.co.absa.atum.model.utils.SerializationUtils

import scala.util.{Failure, Success, Try}

class HttpDispatcher(config: Config) extends Dispatcher with Logging {

  private val serverUrl = config.getString("url")
  private val currentApiVersion = "/api/v1"
  private val createPartitioningEndpoint = Uri.unsafeParse(s"$serverUrl$currentApiVersion/createPartitioning")
  private val createCheckpointEndpoint = Uri.unsafeParse(s"$serverUrl$currentApiVersion/createCheckpoint")
  private val createAdditionalDataEndpoint = Uri.unsafeParse(s"$serverUrl$currentApiVersion/saveMetadata")

  private val commonAtumRequest = basicRequest
    .header("Content-Type", "application/json")
    .response(asString)

  private val backend = HttpURLConnectionBackend()

  logInfo("using http dispatcher")
  logInfo(s"serverUrl $serverUrl")

  override def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO = {
    val request = commonAtumRequest
      .post(createPartitioningEndpoint)
      .body(SerializationUtils.asJson(partitioning))

    val response = backend.send(request)

    SerializationUtils.fromJson[AtumContextDTO](
      safeResponseBody(response).get
    )
  }

  override def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    val request = commonAtumRequest
      .post(createCheckpointEndpoint)
      .body(SerializationUtils.asJson(checkpoint))

    val response = backend.send(request)

    safeResponseBody(response).get
  }

  override def saveAdditionalData(metadataDTO: AdditionalDataSubmitDTO): Unit = {
    val request = commonAtumRequest
      .post(createAdditionalDataEndpoint)
      .body(SerializationUtils.asJson(metadataDTO))

    val response = backend.send(request)

    safeResponseBody(response).get
  }

  private def safeResponseBody(response: Response[Either[String, String]]): Try[String] = {
    response.body match {
      case Left(body) => Failure(HttpException(response.code.code, body))
      case Right(body) => Success(body)
    }
  }

}

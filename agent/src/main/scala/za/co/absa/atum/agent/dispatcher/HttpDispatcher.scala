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
import sttp.capabilities
import sttp.client3._
import sttp.model.Uri
import sttp.client3.okhttp.OkHttpSyncBackend
import za.co.absa.atum.agent.exception.AtumAgentException.HttpException
import za.co.absa.atum.model.dto
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.utils.DTOBase64Encoder.encodeDTO
import za.co.absa.atum.model.utils.JsonSyntaxExtensions._


class HttpDispatcher(config: Config) extends Dispatcher(config) with Logging {
  import HttpDispatcher._

  val serverUrl: String = config.getString(UrlKey)

  private val apiV1 = "/api/v1"
  private val apiV2 = "/api/v2"

  private val partitioningsPath = "partitionings"

  private val createPartitioningEndpoint = Uri.unsafeParse(s"$serverUrl$apiV1/createPartitioning")
  private val createCheckpointEndpoint = Uri.unsafeParse(s"$serverUrl$apiV1/createCheckpoint")

  private val getPartitioningIdEndpoint = Uri.unsafeParse(s"$serverUrl$apiV2/$partitioningsPath")
  private def createAdditionalDataEndpoint(partitioningId: Long): Uri =
    Uri.unsafeParse(s"$serverUrl$apiV2/$partitioningsPath/$partitioningId/additional-data")

  private val commonAtumRequest = basicRequest
    .header("Content-Type", "application/json")
    .response(asString)

  private val backend: SttpBackend[Identity, capabilities.WebSockets] = OkHttpSyncBackend()

  logInfo("using http dispatcher")
  logInfo(s"serverUrl $serverUrl")

  /**
   *  This method is used to get the partitioning ID from the server.
   *  @param partitioning: Partitioning to obtain ID for.
   *  @return Long ID of the partitioning.
   */

  private[dispatcher] def getPartitioningId(partitioning: PartitioningDTO): Long = {
    val encodedPartitioning = encodeDTO(partitioning, dto.encodePartitioningDTO)
    val request = commonAtumRequest.get(getPartitioningIdEndpoint.addParam("partitioning", encodedPartitioning))

    val response = backend.send(request)

    handleResponseBody(response).as[SingleSuccessResponse[PartitioningWithIdDTO]].data.id
  }

  override protected[agent] def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO = {
    val request = commonAtumRequest
      .post(createPartitioningEndpoint)
      .body(partitioning.asJsonString)

    val response = backend.send(request)

    handleResponseBody(response).as[AtumContextDTO]
  }

  override protected[agent] def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    val request = commonAtumRequest
      .post(createCheckpointEndpoint)
      .body(checkpoint.asJsonString)

    val response = backend.send(request)

    handleResponseBody(response)
  }

  override protected[agent] def updateAdditionalData(
    partitioning: PartitioningDTO,
    additionalDataPatchDTO: AdditionalDataPatchDTO
  ): AdditionalDataDTO = {
    val partitioningId = getPartitioningId(partitioning)
    log.debug(s"Got partitioning ID: '$partitioningId'")

    val request = commonAtumRequest
      .patch(createAdditionalDataEndpoint(partitioningId))
      .body(additionalDataPatchDTO.asJsonString)

    val response = backend.send(request)

    handleResponseBody(response).as[SingleSuccessResponse[AdditionalDataDTO]].data
  }

  private def handleResponseBody(response: Response[Either[String, String]]): String = {
    response.body match {
      case Left(body) => throw HttpException(response.code.code, body)
      case Right(body) => body
    }
  }

}

object HttpDispatcher {
  private val UrlKey = "atum.dispatcher.http.url"
}

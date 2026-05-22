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
import okhttp3.OkHttpClient
import org.apache.spark.internal.Logging
import sttp.capabilities
import sttp.client3._
import sttp.model.Uri
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.model.StatusCode
import za.co.absa.atum.agent.exception.AtumAgentException.HttpException
import za.co.absa.atum.model.ApiPaths
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions._

import java.util.concurrent.TimeUnit

class HttpDispatcher(config: Config) extends Dispatcher(config) with Logging {
  import HttpDispatcher._

  private val serverUrl: String = config.getString(UrlKey)

  private val apiV2 = s"/${ApiPaths.Api}/${ApiPaths.V2}"

  private val getPartitioningIdEndpoint = Uri.unsafeParse(s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}")

  private val commonAtumRequest = basicRequest
    .header("Content-Type", "application/json")
    .response(asString)

  private[dispatcher] val backend: SttpBackend[Identity, capabilities.WebSockets] = {
    val connectTimeoutMs = optionalLong(ConnectTimeoutKey).getOrElse(DefaultConnectTimeoutMs)
    val readTimeoutMs = optionalLong(ReadTimeoutKey).getOrElse(DefaultReadTimeoutMs)
    val writeTimeoutMs = optionalLong(WriteTimeoutKey).getOrElse(DefaultWriteTimeoutMs)

    val okHttpClient = new OkHttpClient.Builder()
      .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
      .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
      .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
      .build()

    OkHttpSyncBackend.usingClient(okHttpClient)
  }

  private val httpRetry: HttpRetry = HttpRetry.fromConfig(config)

  /**
   *  This method is used to get the partitioning ID from the server.
   *  @param partitioning: Partitioning to obtain ID for.
   *  @return Long ID of the partitioning.
   */
  private[dispatcher] def getPartitioningId(partitioning: PartitioningDTO): Long = {
    val encodedPartitioning = partitioning.asBase64EncodedJsonString
    val request = commonAtumRequest.get(getPartitioningIdEndpoint.addParam("partitioning", encodedPartitioning))

    val response = withRetry(request)

    handleResponseBody(response).as[SingleSuccessResponse[PartitioningWithIdDTO]].data.id
  }

  private[dispatcher] def getPartitioning(partitioning: PartitioningDTO): Option[PartitioningWithIdDTO] = {
    val encodedPartitioning = partitioning.asBase64EncodedJsonString
    val request = commonAtumRequest.get(getPartitioningIdEndpoint.addParam("partitioning", encodedPartitioning))

    val response = withRetry(request)

    response.code match {
      case StatusCode.NotFound => None
      case _ => Some(handleResponseBody(response).as[SingleSuccessResponse[PartitioningWithIdDTO]].data)
    }
  }

  override protected[agent] def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO = {
    val parentPartitioningIdOpt = partitioning.parentPartitioning.map(getPartitioningId)
    val partitioningWithIdOpt = getPartitioning(partitioning.partitioning)

    val newPartitioningWithIdDTO = partitioningWithIdOpt.getOrElse {
      val endpoint = Uri.unsafeParse(s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}")
      val request = commonAtumRequest
        .post(endpoint)
        .body(
          PartitioningSubmitV2DTO(
            partitioning.partitioning,
            parentPartitioningIdOpt,
            partitioning.authorIfNew
          ).asJsonString
        )
      val response = withRetry(request)
      handleResponseBody(response).as[SingleSuccessResponse[PartitioningWithIdDTO]].data
    }

    val measures = {
      val endpoint = Uri.unsafeParse(
        s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}/${newPartitioningWithIdDTO.id}/${ApiPaths.V2Paths.Measures}"
      )
      val req = commonAtumRequest.get(endpoint)
      val resp = withRetry(req)
      handleResponseBody(resp).as[MultiSuccessResponse[MeasureDTO]].data.toSet
    }

    val additionalData = {
      val endpoint = Uri.unsafeParse(
        s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}/${newPartitioningWithIdDTO.id}/${ApiPaths.V2Paths.AdditionalData}"
      )
      val req = commonAtumRequest.get(endpoint)
      val resp = withRetry(req)
      handleResponseBody(resp).as[MultiSuccessResponse[AdditionalDataItemV2DTO]]
    }

    AtumContextDTO(
      partitioning = newPartitioningWithIdDTO.partitioning,
      measures = measures,
      additionalData = additionalData.data.map(item => item.key -> item.value).toMap
    )
  }

  override protected[agent] def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    val partitioningId = getPartitioningId(checkpoint.partitioning)

    val checkpointV2DTO = CheckpointV2DTO(
      id = checkpoint.id,
      name = checkpoint.name,
      author = checkpoint.author,
      measuredByAtumAgent = checkpoint.measuredByAtumAgent,
      processStartTime = checkpoint.processStartTime,
      processEndTime = checkpoint.processEndTime,
      measurements = checkpoint.measurements,
      properties = checkpoint.properties
    )

    val endpoint = Uri.unsafeParse(
      s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}/$partitioningId/${ApiPaths.V2Paths.Checkpoints}"
    )
    val request = commonAtumRequest
      .post(endpoint)
      .body(checkpointV2DTO.asJsonString)

    val response = withRetry(request)
    handleResponseBody(response)
  }

  override protected[agent] def updateAdditionalData(
    partitioning: PartitioningDTO,
    additionalDataPatchDTO: AdditionalDataPatchDTO
  ): AdditionalDataDTO = {
    val partitioningId = getPartitioningId(partitioning)
    log.debug(s"Got partitioning ID: '$partitioningId'")

    val endpoint = Uri.unsafeParse(
      s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}/$partitioningId/${ApiPaths.V2Paths.AdditionalData}"
    )

    val request = commonAtumRequest
      .patch(endpoint)
      .body(additionalDataPatchDTO.asJsonString)

    val response = withRetry(request)

    val data: AdditionalDataDTO.Data = handleResponseBody(response)
      .as[MultiSuccessResponse[AdditionalDataItemV2DTO]]
      .data
      .map(item =>
        item.value match {
          case Some(_) => item.key -> Some(AdditionalDataItemDTO(item.value.get, item.author))
          case None => item.key -> None
        }
      )
      .toMap

    AdditionalDataDTO(data)
  }

  /**
   * Sends `request` via the sttp backend, retrying on transient failures with exponential backoff.
   * Retries on HTTP 5xx (server errors) and IOException (covers network failures,
   * connection resets, and SocketTimeoutException which OkHttp throws on read/connect timeout).
   */
  private def withRetry(
    request: Request[Either[String, String], capabilities.WebSockets]
  ): Response[Either[String, String]] = {
    httpRetry.retry(backend.send(request))(_.code.code >= StatusCode.InternalServerError.code) {
      (attempt, total, delay, reason) =>
        log.warn(s"Atum agent: $reason on attempt $attempt/$total, retrying in ${delay}ms")
    }
  }

  private def handleResponseBody(response: Response[Either[String, String]]): String = {
    response.body match {
      case Left(body) => throw HttpException(response.code.code, body)
      case Right(body) => body
    }
  }

  private def optionalLong(key: String): Option[Long] =
    if (config.hasPath(key)) Some(config.getLong(key)) else None

}

object HttpDispatcher {
  private val UrlKey = "atum.dispatcher.http.url"
  private val ConnectTimeoutKey = "atum.dispatcher.http.timeout.connect-ms"
  private val ReadTimeoutKey = "atum.dispatcher.http.timeout.read-ms"
  private val WriteTimeoutKey = "atum.dispatcher.http.timeout.write-ms"

  // OkHttp defaults are 10s each; 30s read gives more headroom for slow DB queries under burst load
  private val DefaultConnectTimeoutMs = 10000L
  private val DefaultReadTimeoutMs = 30000L
  private val DefaultWriteTimeoutMs = 10000L
}

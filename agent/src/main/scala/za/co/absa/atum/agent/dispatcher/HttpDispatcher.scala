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
import za.co.absa.atum.model.ApiPaths
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions._

class HttpDispatcher(config: Config) extends Dispatcher(config) with Logging {
  import HttpDispatcher._

  private val serverUrl: String = config.getString(UrlKey)

  private val apiV1 = s"/${ApiPaths.Api}/${ApiPaths.V1}"
  private val apiV2 = s"/${ApiPaths.Api}/${ApiPaths.V2}"

  private val createCheckpointEndpoint = Uri.unsafeParse(s"$serverUrl$apiV1/${ApiPaths.V1Paths.CreateCheckpoint}")

  private val getPartitioningIdEndpoint = Uri.unsafeParse(s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}")
  private def createAdditionalDataEndpoint(partitioningId: Long): Uri =
    Uri.unsafeParse(
      s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}/$partitioningId/${ApiPaths.V2Paths.AdditionalData}"
    )
  private val postPartitioningEndpoint = Uri.unsafeParse(s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}")
  private def createGetPartitioningMeasuresEndpoint(partitioningId: Long) = Uri.unsafeParse(
    s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}/$partitioningId/${ApiPaths.V2Paths.Measures}"
  )
  private def createGetPartitioningAdditionalDataEndpoint(partitioningId: Long) = Uri.unsafeParse(
    s"$serverUrl$apiV2/${ApiPaths.V2Paths.Partitionings}/$partitioningId/${ApiPaths.V2Paths.AdditionalData}"
  )

  private val commonAtumRequest = basicRequest
    .header("Content-Type", "application/json")
    .response(asString)

  private[dispatcher] val backend: SttpBackend[Identity, capabilities.WebSockets] = OkHttpSyncBackend()

  logInfo("using http dispatcher")
  logInfo(s"serverUrl $serverUrl")

  /**
   *  This method is used to get the partitioning ID from the server.
   *  @param partitioning: Partitioning to obtain ID for.
   *  @return Option[Long] ID of the partitioning.
   */
  private[dispatcher] def getPartitioningId(partitioning: PartitioningDTO): Option[Long] = {
    val encodedPartitioning = partitioning.asBase64EncodedJsonString
    val request = commonAtumRequest.get(getPartitioningIdEndpoint.addParam("partitioning", encodedPartitioning))

    val response = backend.send(request)

    response.code.code match {
      case 404 => None
      case _ =>
        Some(handleResponseBody(response).as[SingleSuccessResponse[PartitioningWithIdDTO]].data.id)
    }
  }

  private[dispatcher] def getPartitioning(partitioning: PartitioningDTO): Option[PartitioningWithIdDTO] = {
    val encodedPartitioning = partitioning.asBase64EncodedJsonString
    val request = commonAtumRequest.get(getPartitioningIdEndpoint.addParam("partitioning", encodedPartitioning))

    val response = backend.send(request)

    response.code.code match {
      case 404 => None
      case _ => Some(handleResponseBody(response).as[SingleSuccessResponse[PartitioningWithIdDTO]].data)
    }
  }

  // should be probably renamed, suggestions welcomed :)
  override protected[agent] def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO = {
    val parentPartitioningIdOpt = partitioning.parentPartitioning.flatMap(getPartitioningId)
    val partitioningWithIdOpt = getPartitioning(partitioning.partitioning)

    val newPartitioningWithIdDTO = partitioningWithIdOpt.getOrElse {
      val request = commonAtumRequest
        .post(postPartitioningEndpoint)
        .body(
          PartitioningSubmitV2DTO(
            partitioning.partitioning,
            parentPartitioningIdOpt,
            partitioning.authorIfNew
          ).asJsonString
        )
      val response = backend.send(request)
      handleResponseBody(response).as[SingleSuccessResponse[PartitioningWithIdDTO]].data
    }

    val measures = {
      val req = commonAtumRequest.get(createGetPartitioningMeasuresEndpoint(newPartitioningWithIdDTO.id))
      val resp = backend.send(req)
      handleResponseBody(resp).as[MultiSuccessResponse[MeasureDTO]].data.toSet
    }

    val additionalData = {
      val req = commonAtumRequest.get(createGetPartitioningAdditionalDataEndpoint(newPartitioningWithIdDTO.id))
      val resp = backend.send(req)
      handleResponseBody(resp)
        .as[SingleSuccessResponse[AdditionalDataDTO.Data]]
        .data
        .map(item => item._1 -> item._2.map(_.value))
    }

    AtumContextDTO(
      partitioning = newPartitioningWithIdDTO.partitioning,
      measures = measures,
      additionalData = additionalData
    )
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
      .patch(createAdditionalDataEndpoint(partitioningId.get))
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

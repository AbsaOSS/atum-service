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

package za.co.absa.atum.reader.basic

import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.capabilities
import sttp.client3._
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.model._
import sttp.monad.MonadError
import za.co.absa.atum.model.dto.PartitioningWithIdDTO
import za.co.absa.atum.model.envelopes.NotFoundErrorResponse
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.types.basic.{AtumPartitions, AtumPartitionsOps}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax
import za.co.absa.atum.reader.basic.RequestResult._
import za.co.absa.atum.reader.server.ServerConfig

class PartitioningIdProviderUnitTests extends AnyFunSuiteLike {
  private val serverUrl = "http://localhost:8080"
  private val atumPartitionsToReply = AtumPartitions("a", "b")
  private val atumPartitionsToFailedDecode = AtumPartitions("c", "d")
  private val atumPartitionsToNotFound = AtumPartitions(List.empty)

  private implicit val serverConfig: ServerConfig = ServerConfig(serverUrl)
  private implicit val monad: IdMonad.type = IdMonad
  private implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
    .whenRequestMatches(request => isUriOfAtumPartitions(request.uri, atumPartitionsToReply))
      .thenRespond(SingleSuccessResponse(PartitioningWithIdDTO(1, atumPartitionsToReply.toPartitioningDTO, "Gimli")).asJsonString)
    .whenRequestMatches(request => isUriOfAtumPartitions(request.uri, atumPartitionsToFailedDecode))
      .thenRespond("This is not a correct JSON")
    .whenRequestMatches(request => isUriOfAtumPartitions(request.uri, atumPartitionsToNotFound))
      .thenRespond(NotFoundErrorResponse("Partitioning not found").asJsonString, StatusCode.NotFound)

  private def isUriOfAtumPartitions(uri: Uri, atumPartitions: AtumPartitions): Boolean = {
    val encodedPartitions = atumPartitions.toPartitioningDTO.asBase64EncodedJsonString
    val targetUri = uri"$serverUrl/api/v2/partitionings?partitioning=$encodedPartitions"
    uri == targetUri
  }


  private case class ReaderWithPartitioningIdForTest(partitioning: AtumPartitions)
                                                  (implicit serverConfig: ServerConfig)
        extends Reader[Identity] with PartitioningIdProvider[Identity]{

    override def partitioningId(partitioning: AtumPartitions)
                               (implicit monad: MonadError[Identity]): Identity[RequestResult[Long]] =
      super.partitioningId(partitioning)
  }


  test("Gets the partitioning id") {
    val reader = ReaderWithPartitioningIdForTest(atumPartitionsToReply)
    val response = reader.partitioningId(atumPartitionsToReply)
    val result: Long = response.getOrElse(throw new Exception("Failed to get partitioning id"))
    assert(result == 1)
  }

  test("Not found on the partitioning id") {
    val reader = ReaderWithPartitioningIdForTest(atumPartitionsToNotFound)
    val result = reader.partitioningId(atumPartitionsToNotFound)
    result match {
      case Right(_) => fail("Expected a failure, but OK response received")
      case Left(_: DeserializationException[CirceError]) => fail("Expected a not found response, but deserialization error received")
      case Left(x: HttpError[_]) =>
        assert(x.body.isInstanceOf[NotFoundErrorResponse])
        assert(x.statusCode == StatusCode.NotFound)
      case _ => fail("Unexpected response")
    }
  }

  test("Failure to decode response body") {
    val reader = ReaderWithPartitioningIdForTest(atumPartitionsToFailedDecode)
    val result = reader.partitioningId(atumPartitionsToFailedDecode)
    assert(result.isLeft)
    result.swap.map(e => assert(e.isInstanceOf[DeserializationException[CirceError]]))
  }
}

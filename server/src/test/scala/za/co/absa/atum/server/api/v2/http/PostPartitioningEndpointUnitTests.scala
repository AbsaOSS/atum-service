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

package za.co.absa.atum.server.api.v2.http

import io.circe.parser.decode
import io.circe.syntax._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, mockingDetails, when}
import sttp.client3.circe._
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.{PartitioningSubmitV2DTO, PartitioningWithIdDTO}
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.envelopes.{
  BadRequestResponse,
  ConflictErrorResponse,
  InternalServerErrorResponse,
  NotFoundErrorResponse
}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.v2.controller.{PartitioningController, PartitioningControllerImpl}
import za.co.absa.atum.server.api.v2.service.PartitioningService
import za.co.absa.atum.server.model.CreationResult.Created
import zio._
import zio.test.Assertion.equalTo
import zio.test._

import scala.jdk.CollectionConverters._

object PostPartitioningEndpointUnitTests extends ZIOSpecDefault with TestData {

  private val partitioningControllerMock = mock(classOf[PartitioningController])

  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTO1))
    .thenReturn(ZIO.succeed((StatusCode.Created, SingleSuccessResponse(partitioningWithIdDTO1, uuid1), "location")))

  // 409 means a parent-link cycle (or an existing partitioning with descendants); an existing partitioning is a 200 success, never a conflict
  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTO2))
    .thenReturn(ZIO.fail(ConflictErrorResponse("Partitioning parent link would create a cycle")))

  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTO3))
    .thenReturn(ZIO.fail(InternalServerErrorResponse("error")))

  private val dto101 = partitioningSubmitV2DTO1.copy(parentPartitioningIds = 1L to 101L, author = "p101")

  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTOMultiParent))
    .thenReturn(ZIO.succeed((StatusCode.Created, SingleSuccessResponse(partitioningWithIdDTO1, uuid1), "location")))
  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTOMultiParentNotFound))
    .thenReturn(ZIO.fail(NotFoundErrorResponse("parent not found")))
  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTOMultiParentCycle))
    .thenReturn(ZIO.fail(ConflictErrorResponse("Partitioning parent link would create a cycle")))
  when(partitioningControllerMock.postPartitioning(dto101))
    .thenReturn(ZIO.fail(BadRequestResponse("too many parents")))

  // Existing partitioning: the controller answers 200 + Location with the same body shape (stored author)
  private val existingBody = partitioningWithIdDTO1.copy(author = "stored-author")
  when(partitioningControllerMock.postPartitioning(partitioningSubmitV2DTOExistingAuthor))
    .thenReturn(ZIO.succeed((StatusCode.Ok, SingleSuccessResponse(existingBody, uuid1), "location")))

  private val createPartitioningEndpointMockLayer = ZLayer.succeed(partitioningControllerMock)

  // Second environment: the REAL controller (and decoder) on top of a mocked service, used to prove what a literal
  // JSON body turns into, and the real 100-parent cap.
  private val partitioningServiceMock = mock(classOf[PartitioningService])
  private val createdBody = partitioningWithIdDTO1.copy(author = "svc")
  when(partitioningServiceMock.createPartitioning(any[PartitioningSubmitV2DTO]()))
    .thenReturn(ZIO.succeed(Created(createdBody)))

  private val realControllerLayer: ULayer[PartitioningController] =
    ZLayer.succeed(partitioningServiceMock) >>> PartitioningControllerImpl.layer

  /** DTOs the (mocked) service was really invoked with, by author, as built from the JSON by the real decoder. */
  private def receivedByService(author: String): Seq[PartitioningSubmitV2DTO] =
    mockingDetails(partitioningServiceMock).getInvocations.asScala.toSeq
      .filter(_.getMethod.getName == "createPartitioning")
      .map(_.getArgument[PartitioningSubmitV2DTO](0))
      .filter(_.author == author)

  private def bodyJson(author: String, extra: String): String =
    s"""{"partitioning":${partitioningDTO1.asJson.noSpaces},"author":"$author"$extra}"""

  private val createPartitioningServerEndpoint =
    Endpoints.postPartitioningEndpoint.zServerLogic({
      partitioningSubmitDTO: PartitioningSubmitV2DTO =>
        PartitioningController.postPartitioning(partitioningSubmitDTO)
      }
    )

  private val backendStub =
    TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpoint(createPartitioningServerEndpoint)
      .thenRunLogic()
      .backend()

  private def rawPost(json: String) =
    basicRequest
      .post(uri"https://test.com/api/v2/partitionings")
      .contentType("application/json")
      .body(json)
      .response(asStringAlways)
      .send(backendStub)

  def spec: Spec[TestEnvironment with Scope, Any] = {
    val request = basicRequest
      .post(uri"https://test.com/api/v2/partitionings")
      .response(asJson[SingleSuccessResponse[PartitioningWithIdDTO]])

    val statusMappingSuite = suite("CreatePartitioningEndpointSuite")(
      test("Returns expected PartitioningWithIdDTO and location header") {
        val response = request
          .body(partitioningSubmitV2DTO1)
          .send(backendStub)

        val body = response.map(_.body)
        val statusCode = response.map(_.code)
        val header = response.map(_.header("Location"))

        assertZIO(body <&> statusCode <&> header)(
          equalTo(Right(SingleSuccessResponse(partitioningWithIdDTO1, uuid1)), StatusCode.Created, Some("location"))
        )
      },
      test("Multi-parent body returns 201, envelope and Location header") {
        val response = request.body(partitioningSubmitV2DTOMultiParent).send(backendStub)
        assertZIO(response.map(r => (r.body, r.code, r.header("Location"))))(
          equalTo((Right(SingleSuccessResponse(partitioningWithIdDTO1, uuid1)), StatusCode.Created, Some("location")))
        )
      },
      test("Existing partitioning returns 200, Location header and the same body shape") {
        val response = request.body(partitioningSubmitV2DTOExistingAuthor).send(backendStub)
        assertZIO(response.map(r => (r.body, r.code, r.header("Location"))))(
          equalTo((Right(SingleSuccessResponse(existingBody, uuid1)), StatusCode.Ok, Some("location")))
        )
      },
      test("Invalid JSON returns 400") {
        val response = request.contentType("application/json").body("{not json").send(backendStub)
        assertZIO(response.map(_.code))(equalTo(StatusCode.BadRequest))
      },
      test("Missing partitioning returns 400") {
        val response = request.contentType("application/json").body("""{"author":"a"}""").send(backendStub)
        assertZIO(response.map(_.code))(equalTo(StatusCode.BadRequest))
      },
      test("Missing parent returns 404") {
        assertZIO(request.body(partitioningSubmitV2DTOMultiParentNotFound).send(backendStub).map(_.code))(
          equalTo(StatusCode.NotFound)
        )
      },
      test("Cycle returns 409") {
        assertZIO(request.body(partitioningSubmitV2DTOMultiParentCycle).send(backendStub).map(_.code))(
          equalTo(StatusCode.Conflict)
        )
      },
      // NOTE: the controller is a stub here; this only proves the BadRequestResponse -> HTTP 400 rendering.
      // The cap itself is exercised end to end in "RealControllerDecoderSuite".
      test("A BadRequestResponse from the controller is rendered as 400") {
        assertZIO(request.body(dto101).send(backendStub).map(_.code))(equalTo(StatusCode.BadRequest))
      },
      test("Returns expected Conflict (409, cycle)") {
        val response = request
          .body(partitioningSubmitV2DTO2)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.Conflict))
      },
      test("Returns expected InternalServerError") {
        val response = request
          .body(partitioningSubmitV2DTO3)
          .send(backendStub)

        val statusCode = response.map(_.code)

        assertZIO(statusCode)(equalTo(StatusCode.InternalServerError))
      }
    ).provide(createPartitioningEndpointMockLayer)

    val realControllerSuite = suite("RealControllerDecoderSuite")(
      test("Legacy JSON (parentPartitioningId) is decoded and reaches the service as parentPartitioningIds = [5]") {
        for {
          response <- rawPost(bodyJson("legacyAuthor", ""","parentPartitioningId":5"""))
          received = receivedByService("legacyAuthor")
        } yield assertTrue(
          response.code == StatusCode.Created,
          response.header("Location").contains(s"/api/v2/partitionings/${partitioningWithIdDTO1.id}"),
          received.size == 1,
          received.head.parentPartitioningIds == Seq(5L),
          received.head.partitioning == partitioningDTO1,
          !received.head.copyMeasureDefinitions
        )
      },
      test("Legacy numeric-string parentPartitioningId is decoded to [7]") {
        for {
          response <- rawPost(bodyJson("legacyStringAuthor", ""","parentPartitioningId":"7""""))
          received = receivedByService("legacyStringAuthor")
        } yield assertTrue(
          response.code == StatusCode.Created,
          received.map(_.parentPartitioningIds) == Seq(Seq(7L))
        )
      },
      test("Both keys are merged and de-duplicated by the decoder") {
        for {
          response <- rawPost(
            bodyJson("bothKeysAuthor", ""","parentPartitioningId":5,"parentPartitioningIds":[5,6,6]""")
          )
          received = receivedByService("bothKeysAuthor")
        } yield assertTrue(
          response.code == StatusCode.Created,
          received.map(_.parentPartitioningIds) == Seq(Seq(5L, 6L))
        )
      },
      test("Body without parents and without copyMeasureDefinitions -> no parents, flag false") {
        for {
          response <- rawPost(bodyJson("noFlagAuthor", ""))
          received = receivedByService("noFlagAuthor")
        } yield assertTrue(
          response.code == StatusCode.Created,
          received.size == 1,
          received.head.parentPartitioningIds.isEmpty,
          !received.head.copyMeasureDefinitions
        )
      },
      test("Explicit \"copyMeasureDefinitions\":null is decoded to false") {
        for {
          response <- rawPost(bodyJson("nullFlagAuthor", ""","copyMeasureDefinitions":null"""))
          received = receivedByService("nullFlagAuthor")
        } yield assertTrue(
          response.code == StatusCode.Created,
          received.size == 1,
          !received.head.copyMeasureDefinitions
        )
      },
      test("\"copyMeasureDefinitions\":true is decoded to true and reaches the service") {
        val extra = ""","parentPartitioningIds":[5],"copyMeasureDefinitions":true"""
        for {
          response <- rawPost(bodyJson("flagTrueAuthor", extra))
          received = receivedByService("flagTrueAuthor")
        } yield assertTrue(
          response.code == StatusCode.Created,
          received.size == 1,
          received.head.copyMeasureDefinitions,
          received.head.parentPartitioningIds == Seq(5L)
        )
      },
      test("101 distinct parents -> 400 BadRequest envelope from the real controller; service is not called") {
        val ids = (1L to 101L).mkString(",")
        for {
          response <- rawPost(bodyJson("tooManyAuthor", s""","parentPartitioningIds":[$ids]"""))
          error = decode[BadRequestResponse](response.body)
        } yield assertTrue(
          response.code == StatusCode.BadRequest,
          error.map(_.message) == Right("Too many parent partitionings: 101, maximum allowed is 100"),
          receivedByService("tooManyAuthor").isEmpty
        )
      },
      test("exactly 100 distinct parents are accepted") {
        val ids = (1L to 100L).mkString(",")
        for {
          response <- rawPost(bodyJson("hundredAuthor", s""","parentPartitioningIds":[$ids]"""))
          received = receivedByService("hundredAuthor")
        } yield assertTrue(
          response.code == StatusCode.Created,
          received.map(_.parentPartitioningIds) == Seq((1L to 100L).toSeq)
        )
      },
      test("the cap counts DISTINCT ids: 150 raw ids with 100 distinct values are accepted") {
        val ids = ((1L to 100L) ++ (1L to 50L)).mkString(",")
        for {
          response <- rawPost(bodyJson("dupHeavyAuthor", s""","parentPartitioningIds":[$ids]"""))
          received = receivedByService("dupHeavyAuthor")
        } yield assertTrue(
          response.code == StatusCode.Created,
          received.map(_.parentPartitioningIds.size) == Seq(100)
        )
      }
    ).provide(realControllerLayer)

    suite("PostPartitioningEndpointSuite")(statusMappingSuite, realControllerSuite)
  }

}

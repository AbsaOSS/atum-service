package za.co.absa.atum.agent.dispatcher

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, Ignore}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import sttp.client3._
import sttp.model.StatusCode
import com.typesafe.config.Config
import sttp.capabilities
import za.co.absa.atum.agent.exception.AtumAgentException.HttpException
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.{JsonDeserializationSyntax, JsonSerializationSyntax}

import java.time.ZonedDateTime

class HttpDispatcherUnitTests extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  var mockBackend: SttpBackend[Identity, sttp.capabilities.WebSockets] = _
  var mockConfig: Config = _
  val serverUrl = "http://test-server"

  val testPartitioningDTO: Seq[PartitionDTO] = Seq(PartitionDTO("k", "v"))
  val testPartitioningSubmitDTO = PartitioningSubmitDTO(testPartitioningDTO, None, "author")
  val createdPartitioningWithId = PartitioningWithIdDTO(123L, testPartitioningDTO, "author")
  val measures: Seq[MeasureDTO] = Seq(MeasureDTO("m1", Seq("c1")), MeasureDTO("m2", Seq("c2")))
  val additionalData: Seq[AdditionalDataItemV2DTO] = Seq(
    AdditionalDataItemV2DTO("key1", Some("val1"), "author1"),
    AdditionalDataItemV2DTO("key2", None, "author2")
  )

  def encodedPartitioning(partitioning: Seq[PartitionDTO]): String =
    partitioning.asBase64EncodedJsonString

  override def beforeEach(): Unit = {
    mockBackend = mock(classOf[SttpBackend[Identity, sttp.capabilities.WebSockets]])
    mockConfig = mock(classOf[Config])
    when(mockConfig.getString(any[String])).thenReturn(serverUrl)
  }

  def dispatcher: HttpDispatcher = new HttpDispatcher(mockConfig) {
    override private[dispatcher] val backend = mockBackend
  }

  def isGetPartitioningRequest(
    partitioning: Seq[PartitionDTO]
  ): Request[Either[String, String], capabilities.WebSockets] =
    argThat(new org.mockito.ArgumentMatcher[Request[Either[String, String], sttp.capabilities.WebSockets]] {
      override def matches(req: Request[Either[String, String], sttp.capabilities.WebSockets]): Boolean =
        req != null &&
          req.method.method == "GET" &&
          req.uri.path.mkString.contains("partitionings") &&
          req.uri.params.toSeq.exists { case (k, v) =>
            k == "partitioning" && v == encodedPartitioning(partitioning)
          }
    })

  def isPostPartitioningRequest: Request[Either[String, String], capabilities.WebSockets] =
    argThat(new org.mockito.ArgumentMatcher[Request[Either[String, String], sttp.capabilities.WebSockets]] {
      override def matches(req: Request[Either[String, String], sttp.capabilities.WebSockets]): Boolean =
        req != null && req.method.method == "POST" && req.uri.path.mkString.contains("partitionings")
    })

  def isGetMeasuresRequest: Request[Either[String, String], capabilities.WebSockets] =
    argThat(new org.mockito.ArgumentMatcher[Request[Either[String, String], sttp.capabilities.WebSockets]] {
      override def matches(req: Request[Either[String, String], sttp.capabilities.WebSockets]): Boolean =
        req != null && req.method.method == "GET" && req.uri.path.mkString.contains("measures")
    })

  def isGetAdditionalDataRequest: Request[Either[String, String], capabilities.WebSockets] =
    argThat(new org.mockito.ArgumentMatcher[Request[Either[String, String], sttp.capabilities.WebSockets]] {
      override def matches(req: Request[Either[String, String], sttp.capabilities.WebSockets]): Boolean =
        req != null && req.method.method == "GET" && req.uri.path.mkString.contains("additional-data")
    })

  def stubGetPartitioning(partitioning: Seq[PartitionDTO], response: Response[Either[String, String]]): Unit =
    when(mockBackend.send(isGetPartitioningRequest(partitioning))).thenReturn(response)

  def stubPostPartitioning(response: Response[Either[String, String]]): Unit =
    when(mockBackend.send(isPostPartitioningRequest)).thenReturn(response)

  def stubGetMeasures(response: Response[Either[String, String]]): Unit =
    when(mockBackend.send(isGetMeasuresRequest)).thenReturn(response)

  def stubGetAdditionalData(response: Response[Either[String, String]]): Unit =
    when(mockBackend.send(isGetAdditionalDataRequest)).thenReturn(response)

  "createPartitioning" should "create and return AtumContextDTO when partitioning does not exist" in {
    val parentPartitioning = Seq(PartitionDTO("parentK", "parentV"))
    val parentPartitioningId = 999L
    val parentPartitioningWithId = PartitioningWithIdDTO(parentPartitioningId, parentPartitioning, "parentAuthor")
    val getParentResponse = Response(
      Right(SingleSuccessResponse(parentPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    val getPartitioningResponse = Response(Left("Not found"): Either[String, String], StatusCode.NotFound)
    val postPartitioningResponse = Response(
      Right(SingleSuccessResponse(createdPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Created
    )
    val measuresResponse =
      Response(Right(MultiSuccessResponse(measures).asJsonString): Either[String, String], StatusCode.Ok)
    val additionalDataResponse =
      Response(Right(MultiSuccessResponse[AdditionalDataItemV2DTO](additionalData).asJsonString): Either[String, String], StatusCode.Ok)

    stubGetPartitioning(parentPartitioning, getParentResponse)
    stubGetPartitioning(testPartitioningDTO, getPartitioningResponse)
    stubPostPartitioning(postPartitioningResponse)
    stubGetMeasures(measuresResponse)
    stubGetAdditionalData(additionalDataResponse)

    val dispatcherWithMocks = dispatcher
    val result = dispatcherWithMocks.createPartitioning(
      PartitioningSubmitDTO(testPartitioningDTO, Some(parentPartitioning), "author")
    )

    result.partitioning shouldBe createdPartitioningWithId.partitioning
    result.measures shouldBe measures.toSet
    result.additionalData should contain key "key1"
    result.additionalData should contain key "key2"
    result.additionalData("key1") shouldBe Some("val1")
    result.additionalData("key2") shouldBe None
  }

  it should "return AtumContextDTO for existing partitioning without creating a new one" in {
    val existingPartitioningWithId = PartitioningWithIdDTO(123L, testPartitioningDTO, "author")
    val getPartitioningResponse = Response(
      Right(SingleSuccessResponse(existingPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    val measuresResponse =
      Response(Right(MultiSuccessResponse(measures).asJsonString): Either[String, String], StatusCode.Ok)
    val additionalDataResponse =
      Response(Right(SingleSuccessResponse(additionalData).asJsonString): Either[String, String], StatusCode.Ok)

    stubGetPartitioning(testPartitioningDTO, getPartitioningResponse)
    stubGetMeasures(measuresResponse)
    stubGetAdditionalData(additionalDataResponse)

    val dispatcherWithMocks = dispatcher
    val result = dispatcherWithMocks.createPartitioning(testPartitioningSubmitDTO)

    result.partitioning shouldBe existingPartitioningWithId.partitioning
    result.measures shouldBe measures.toSet
    result.additionalData should contain key "key1"
    result.additionalData should contain key "key2"
    result.additionalData("key1") shouldBe Some("val1")
    result.additionalData("key2") shouldBe None

    verify(mockBackend, never()).send(isPostPartitioningRequest)
  }

  it should "handle empty measures and additional data for a new partitioning" in {
    val getPartitioningResponse = Response(Left("Not found"): Either[String, String], StatusCode.NotFound)
    val postPartitioningResponse = Response(
      Right(SingleSuccessResponse(createdPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Created
    )
    val measuresResponse =
      Response(Right(MultiSuccessResponse(Seq.empty[MeasureDTO]).asJsonString): Either[String, String], StatusCode.Ok)
    val additionalDataResponse = Response(
      Right(MultiSuccessResponse(Seq.empty[AdditionalDataItemV2DTO]).asJsonString): Either[
        String,
        String
      ],
      StatusCode.Ok
    )

    stubGetPartitioning(testPartitioningDTO, getPartitioningResponse)
    stubPostPartitioning(postPartitioningResponse)
    stubGetMeasures(measuresResponse)
    stubGetAdditionalData(additionalDataResponse)

    val dispatcherWithMocks = dispatcher
    val result = dispatcherWithMocks.createPartitioning(testPartitioningSubmitDTO)

    result.partitioning shouldBe createdPartitioningWithId.partitioning
    result.measures shouldBe empty
    result.additionalData shouldBe empty
  }

  "saveCheckpoint" should "successfully save a checkpoint when partitioning exists" in {
    val checkpoint = CheckpointDTO(
      id = java.util.UUID.randomUUID(),
      name = "cp",
      author = "author",
      measuredByAtumAgent = true,
      processStartTime = ZonedDateTime.now(),
      processEndTime = Some(ZonedDateTime.now()),
      measurements = Set.empty,
      partitioning = testPartitioningDTO
    )
    val partitioningWithId = PartitioningWithIdDTO(123L, testPartitioningDTO, "author")
    val getPartitioningResponse = Response(
      Right(SingleSuccessResponse(partitioningWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    val postCheckpointResponse = Response(Right("{}"): Either[String, String], StatusCode.Created)

    stubGetPartitioning(testPartitioningDTO, getPartitioningResponse)
    when(
      mockBackend.send(
        argThat[Request[Either[String, String], capabilities.WebSockets]](
          req => req.method.method == "POST" && req.uri.path.mkString.contains("checkpoints")
        )
      )
    ).thenReturn(postCheckpointResponse)

    noException should be thrownBy dispatcher.saveCheckpoint(checkpoint)
  }

  it should "throw HttpException if partitioning is not found" in {
    val checkpoint = CheckpointDTO(
      id = java.util.UUID.randomUUID(),
      name = "cp",
      author = "author",
      measuredByAtumAgent = true,
      processStartTime = ZonedDateTime.now(),
      processEndTime = Some(ZonedDateTime.now()),
      measurements = Set.empty,
      partitioning = testPartitioningDTO
    )
    val notFoundResponse = Response(Left("Not found"): Either[String, String], StatusCode.NotFound)

    stubGetPartitioning(testPartitioningDTO, notFoundResponse)

    an[HttpException] should be thrownBy dispatcher.saveCheckpoint(checkpoint)
  }

  it should "throw HttpException if server returns error on checkpoint save" in {
    val checkpoint = CheckpointDTO(
      id = java.util.UUID.randomUUID(),
      name = "cp",
      author = "author",
      measuredByAtumAgent = true,
      processStartTime = ZonedDateTime.now(),
      processEndTime = Some(ZonedDateTime.now()),
      measurements = Set.empty,
      partitioning = testPartitioningDTO
    )
    val partitioningWithId = PartitioningWithIdDTO(123L, testPartitioningDTO, "author")
    val getPartitioningResponse = Response(
      Right(SingleSuccessResponse(partitioningWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    val badRequestResponse = Response(Left("Bad request"): Either[String, String], StatusCode.BadRequest)

    stubGetPartitioning(testPartitioningDTO, getPartitioningResponse)
    when(
      mockBackend.send(
        argThat[Request[Either[String, String], capabilities.WebSockets]](
          req => req.method.method == "POST" && req.uri.path.mkString.contains("checkpoints")
        )
      )
    ).thenReturn(badRequestResponse)

    an[HttpException] should be thrownBy dispatcher.saveCheckpoint(checkpoint)
  }

  it should "treat HTTP 409 Conflict as success (checkpoint already saved on prior attempt)" in {
    val checkpoint = CheckpointDTO(
      id = java.util.UUID.randomUUID(),
      name = "cp",
      author = "author",
      measuredByAtumAgent = true,
      processStartTime = ZonedDateTime.now(),
      processEndTime = Some(ZonedDateTime.now()),
      measurements = Set.empty,
      partitioning = testPartitioningDTO
    )
    val partitioningWithId = PartitioningWithIdDTO(123L, testPartitioningDTO, "author")
    val getPartitioningResponse = Response(
      Right(SingleSuccessResponse(partitioningWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    val conflictResponse = Response(Left("Checkpoint already present"): Either[String, String], StatusCode.Conflict)

    stubGetPartitioning(testPartitioningDTO, getPartitioningResponse)
    when(
      mockBackend.send(
        argThat[Request[Either[String, String], capabilities.WebSockets]](
          req => req.method.method == "POST" && req.uri.path.mkString.contains("checkpoints")
        )
      )
    ).thenReturn(conflictResponse)

    // Should NOT throw — 409 means the checkpoint was already saved
    noException should be thrownBy dispatcher.saveCheckpoint(checkpoint)
  }

  "createPartitioning" should "ensure parent link via PATCH when child already exists" in {
    val parentPartitioning = Seq(PartitionDTO("parentK", "parentV"))
    val parentPartitioningId = 999L
    val parentPartitioningWithId = PartitioningWithIdDTO(parentPartitioningId, parentPartitioning, "parentAuthor")
    val existingChildWithId = PartitioningWithIdDTO(123L, testPartitioningDTO, "author")

    // Parent lookup succeeds
    val getParentResponse = Response(
      Right(SingleSuccessResponse(parentPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    // Child already exists (returns OK, not 404)
    val getChildResponse = Response(
      Right(SingleSuccessResponse(existingChildWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    val measuresResponse =
      Response(Right(MultiSuccessResponse(measures).asJsonString): Either[String, String], StatusCode.Ok)
    val additionalDataResponse =
      Response(Right(MultiSuccessResponse[AdditionalDataItemV2DTO](additionalData).asJsonString): Either[String, String], StatusCode.Ok)
    val patchAncestorsResponse = Response(Right(""): Either[String, String], StatusCode.NoContent)

    stubGetPartitioning(parentPartitioning, getParentResponse)
    stubGetPartitioning(testPartitioningDTO, getChildResponse)
    stubGetMeasures(measuresResponse)
    stubGetAdditionalData(additionalDataResponse)
    when(mockBackend.send(isPatchAncestorsRequest)).thenReturn(patchAncestorsResponse)

    val dispatcherWithMocks = dispatcher
    val result = dispatcherWithMocks.createPartitioning(
      PartitioningSubmitDTO(testPartitioningDTO, Some(parentPartitioning), "author")
    )

    result.partitioning shouldBe existingChildWithId.partitioning

    // Verify PATCH was called to ensure the parent link
    verify(mockBackend).send(isPatchAncestorsRequest)
    // Verify POST was never called (child already existed)
    verify(mockBackend, never()).send(isPostPartitioningRequest)
  }

  it should "not call PATCH ancestors when child is newly created (link established at creation time)" in {
    val parentPartitioning = Seq(PartitionDTO("parentK", "parentV"))
    val parentPartitioningId = 999L
    val parentPartitioningWithId = PartitioningWithIdDTO(parentPartitioningId, parentPartitioning, "parentAuthor")

    val getParentResponse = Response(
      Right(SingleSuccessResponse(parentPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    // Child does NOT exist
    val getChildResponse = Response(Left("Not found"): Either[String, String], StatusCode.NotFound)
    val postPartitioningResponse = Response(
      Right(SingleSuccessResponse(createdPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Created
    )
    val measuresResponse =
      Response(Right(MultiSuccessResponse(measures).asJsonString): Either[String, String], StatusCode.Ok)
    val additionalDataResponse =
      Response(Right(MultiSuccessResponse[AdditionalDataItemV2DTO](additionalData).asJsonString): Either[String, String], StatusCode.Ok)

    stubGetPartitioning(parentPartitioning, getParentResponse)
    stubGetPartitioning(testPartitioningDTO, getChildResponse)
    stubPostPartitioning(postPartitioningResponse)
    stubGetMeasures(measuresResponse)
    stubGetAdditionalData(additionalDataResponse)

    val dispatcherWithMocks = dispatcher
    dispatcherWithMocks.createPartitioning(
      PartitioningSubmitDTO(testPartitioningDTO, Some(parentPartitioning), "author")
    )

    // PATCH should NOT be called — the POST already established the link
    verify(mockBackend, never()).send(isPatchAncestorsRequest)
  }

  it should "treat 409 Conflict on ensure-link PATCH as success (link already exists)" in {
    val parentPartitioning = Seq(PartitionDTO("parentK", "parentV"))
    val parentPartitioningId = 999L
    val parentPartitioningWithId = PartitioningWithIdDTO(parentPartitioningId, parentPartitioning, "parentAuthor")
    val existingChildWithId = PartitioningWithIdDTO(123L, testPartitioningDTO, "author")

    val getParentResponse = Response(
      Right(SingleSuccessResponse(parentPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    val getChildResponse = Response(
      Right(SingleSuccessResponse(existingChildWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    val measuresResponse =
      Response(Right(MultiSuccessResponse(measures).asJsonString): Either[String, String], StatusCode.Ok)
    val additionalDataResponse =
      Response(Right(MultiSuccessResponse[AdditionalDataItemV2DTO](additionalData).asJsonString): Either[String, String], StatusCode.Ok)
    val conflictResponse = Response(Left("Link already exists"): Either[String, String], StatusCode.Conflict)

    stubGetPartitioning(parentPartitioning, getParentResponse)
    stubGetPartitioning(testPartitioningDTO, getChildResponse)
    stubGetMeasures(measuresResponse)
    stubGetAdditionalData(additionalDataResponse)
    when(mockBackend.send(isPatchAncestorsRequest)).thenReturn(conflictResponse)

    val dispatcherWithMocks = dispatcher

    // Should NOT throw — 409 on PATCH ancestors is expected
    noException should be thrownBy dispatcherWithMocks.createPartitioning(
      PartitioningSubmitDTO(testPartitioningDTO, Some(parentPartitioning), "author")
    )
  }

  // ---- multi-parent (D-02, D-03) ----

  private def okResp(body: String, code: StatusCode = StatusCode.Ok): Response[Either[String, String]] =
    Response(Right(body): Either[String, String], code)

  private def stubParent(parent: Seq[PartitionDTO], id: Long): Unit =
    stubGetPartitioning(parent, okResp(SingleSuccessResponse(PartitioningWithIdDTO(id, parent, "pa")).asJsonString))

  private def stubTail(): Unit = {
    stubGetMeasures(okResp(MultiSuccessResponse(measures).asJsonString))
    stubGetAdditionalData(okResp(MultiSuccessResponse[AdditionalDataItemV2DTO](additionalData).asJsonString))
  }

  private def capturePostBodies(): Seq[PartitioningSubmitV2DTO] = {
    import scala.collection.JavaConverters._
    val captor = org.mockito.ArgumentCaptor.forClass(classOf[Request[Either[String, String], capabilities.WebSockets]])
    verify(mockBackend, atLeastOnce()).send(captor.capture())
    captor.getAllValues.asScala.toList
      .filter(r => r.method.method == "POST" && r.uri.path.mkString.contains("partitionings"))
      .map { r =>
        val body = r.body match {
          case StringBody(s, _, _) => s
          case other               => fail(s"Unexpected body type: $other")
        }
        body.as[PartitioningSubmitV2DTO]
      }
  }

  private def parentsN(n: Int): Seq[Seq[PartitionDTO]] = (1 to n).map(i => Seq(PartitionDTO(s"pk$i", s"pv$i")))

  private def runMulti(n: Int, childExists: Boolean): Unit = {
    val parents = parentsN(n)
    parents.zipWithIndex.foreach { case (p, i) => stubParent(p, 901L + i) }
    if (childExists) {
      stubGetPartitioning(testPartitioningDTO, okResp(SingleSuccessResponse(createdPartitioningWithId).asJsonString))
    }
    stubPostPartitioning(okResp(SingleSuccessResponse(createdPartitioningWithId).asJsonString, StatusCode.Created))
    stubTail()
    val result = dispatcher.createPartitioning(PartitioningSubmitDTO(testPartitioningDTO, parents, "author"))
    result.partitioning shouldBe createdPartitioningWithId.partitioning

    verify(mockBackend, times(1)).send(isPostPartitioningRequest)
    verify(mockBackend, never()).send(isPatchAncestorsRequest)
    verify(mockBackend, never()).send(isGetPartitioningRequest(testPartitioningDTO))
    val bodies = capturePostBodies()
    bodies should have size 1
    bodies.head.parentPartitioningIds shouldBe (0 until n).map(901L + _)
    bodies.head.partitioning shouldBe testPartitioningDTO
    bodies.head.author shouldBe "author"
  }

  it should "send ONE POST carrying all parent ids in order for 2 parents (child absent)" in {
    runMulti(2, childExists = false)
  }

  it should "send ONE POST, no child GET and no PATCH for 2 parents when child already exists" in {
    runMulti(2, childExists = true)
  }

  it should "send all three parent ids in order in one POST for 3 parents" in {
    runMulti(3, childExists = false)
  }

  it should "propagate HttpException and send no POST when any parent is not found" in {
    val parents = parentsN(2)
    stubParent(parents.head, 901L)
    stubGetPartitioning(parents(1), Response(Left("Not found"): Either[String, String], StatusCode.NotFound))
    stubPostPartitioning(okResp(SingleSuccessResponse(createdPartitioningWithId).asJsonString, StatusCode.Created))
    stubTail()

    an[HttpException] should be thrownBy
      dispatcher.createPartitioning(PartitioningSubmitDTO(testPartitioningDTO, parents, "author"))
    verify(mockBackend, never()).send(isPostPartitioningRequest)
  }

  it should "keep 0-parent flow: child GET, then POST with empty parentPartitioningIds" in {
    stubGetPartitioning(testPartitioningDTO, Response(Left("Not found"): Either[String, String], StatusCode.NotFound))
    stubPostPartitioning(okResp(SingleSuccessResponse(createdPartitioningWithId).asJsonString, StatusCode.Created))
    stubTail()

    dispatcher.createPartitioning(PartitioningSubmitDTO(testPartitioningDTO, Seq.empty[PartitioningDTO], "author"))

    verify(mockBackend).send(isGetPartitioningRequest(testPartitioningDTO))
    val bodies = capturePostBodies()
    bodies should have size 1
    bodies.head.parentPartitioningIds shouldBe Seq.empty[Long]
  }

  private def isPatchAncestorsRequest: Request[Either[String, String], capabilities.WebSockets] =
    argThat(new org.mockito.ArgumentMatcher[Request[Either[String, String], sttp.capabilities.WebSockets]] {
      override def matches(req: Request[Either[String, String], sttp.capabilities.WebSockets]): Boolean =
        req != null && req.method.method == "PATCH" && req.uri.path.mkString.contains("ancestors")
    })

}
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
import za.co.absa.atum.agent.model.PartitioningCreateRequest
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.{JsonDeserializationSyntax, JsonSerializationSyntax}

import java.time.ZonedDateTime

class HttpDispatcherUnitTests extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  var mockBackend: SttpBackend[Identity, sttp.capabilities.WebSockets] = _
  var mockConfig: Config = _
  val serverUrl = "http://test-server"

  val testPartitioningDTO: Seq[PartitionDTO] = Seq(PartitionDTO("k", "v"))
  val testPartitioningCreateRequest = PartitioningCreateRequest(testPartitioningDTO, None, "author")
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
    val postPartitioningResponse = Response(
      Right(SingleSuccessResponse(createdPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Created
    )
    val measuresResponse =
      Response(Right(MultiSuccessResponse(measures).asJsonString): Either[String, String], StatusCode.Ok)
    val additionalDataResponse =
      Response(Right(MultiSuccessResponse[AdditionalDataItemV2DTO](additionalData).asJsonString): Either[String, String], StatusCode.Ok)

    stubGetPartitioning(parentPartitioning, getParentResponse)
    stubPostPartitioning(postPartitioningResponse)
    stubGetMeasures(measuresResponse)
    stubGetAdditionalData(additionalDataResponse)

    val dispatcherWithMocks = dispatcher
    val result = dispatcherWithMocks.createPartitioning(
      PartitioningCreateRequest(testPartitioningDTO, Some(parentPartitioning), "author")
    )

    result.partitioning shouldBe createdPartitioningWithId.partitioning
    result.measures shouldBe measures.toSet
    result.additionalData should contain key "key1"
    result.additionalData should contain key "key2"
    result.additionalData("key1") shouldBe Some("val1")
    result.additionalData("key2") shouldBe None

    // exactly one POST, the child is never looked up with a GET
    verify(mockBackend, times(1)).send(isPostPartitioningRequest)
    verify(mockBackend, never()).send(isGetPartitioningRequest(testPartitioningDTO))
  }

  it should "return AtumContextDTO built from the POST answer 200 OK for an existing partitioning (one POST)" in {
    val existingPartitioningWithId = PartitioningWithIdDTO(123L, testPartitioningDTO, "stored-author")
    val postPartitioningResponse = Response(
      Right(SingleSuccessResponse(existingPartitioningWithId).asJsonString): Either[String, String],
      StatusCode.Ok
    )
    val measuresResponse =
      Response(Right(MultiSuccessResponse(measures).asJsonString): Either[String, String], StatusCode.Ok)
    val additionalDataResponse =
      Response(Right(MultiSuccessResponse[AdditionalDataItemV2DTO](additionalData).asJsonString): Either[String, String], StatusCode.Ok)

    stubPostPartitioning(postPartitioningResponse)
    stubGetMeasures(measuresResponse)
    stubGetAdditionalData(additionalDataResponse)

    val dispatcherWithMocks = dispatcher
    val result = dispatcherWithMocks.createPartitioning(testPartitioningCreateRequest)

    result.partitioning shouldBe existingPartitioningWithId.partitioning
    result.measures shouldBe measures.toSet
    result.additionalData should contain key "key1"
    result.additionalData should contain key "key2"
    result.additionalData("key1") shouldBe Some("val1")
    result.additionalData("key2") shouldBe None

    verify(mockBackend, times(1)).send(isPostPartitioningRequest)
    verify(mockBackend, never()).send(isGetPartitioningRequest(testPartitioningDTO))
    verify(mockBackend, never()).send(isPatchAncestorsRequest)
  }

  it should "handle empty measures and additional data for a new partitioning" in {
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

    stubPostPartitioning(postPartitioningResponse)
    stubGetMeasures(measuresResponse)
    stubGetAdditionalData(additionalDataResponse)

    val dispatcherWithMocks = dispatcher
    val result = dispatcherWithMocks.createPartitioning(testPartitioningCreateRequest)

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

  "createPartitioning" should "send ONE POST and surface HTTP 409 (cycle) as HttpException without PATCH or retry" in {
    val parents = parentsN(1)
    stubParent(parents.head, 901L)
    stubPostPartitioning(Response(Left("cycle detected"): Either[String, String], StatusCode.Conflict))
    stubTail()

    val ex = the[HttpException] thrownBy
      dispatcher.createPartitioning(PartitioningCreateRequest(testPartitioningDTO, parents, "author"))
    ex.statusCode shouldBe 409

    verify(mockBackend, times(1)).send(isPostPartitioningRequest)
    verify(mockBackend, never()).send(isPatchAncestorsRequest)
    verify(mockBackend, never()).send(isGetPartitioningRequest(testPartitioningDTO))
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
    val captor = org.mockito.ArgumentCaptor.forClass(classOf[Request[Either[String, String], capabilities.WebSockets]])
    verify(mockBackend, atLeastOnce()).send(captor.capture())
    (0 until captor.getAllValues.size).map(captor.getAllValues.get).toList
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

  private def runMatrix(n: Int, status: StatusCode, flag: Boolean): Unit = {
    val parents = parentsN(n)
    parents.zipWithIndex.foreach { case (p, i) => stubParent(p, 901L + i) }
    val storedAuthor = if (status == StatusCode.Ok) "stored-author" else "author"
    val stored = PartitioningWithIdDTO(123L, testPartitioningDTO, storedAuthor)
    stubPostPartitioning(okResp(SingleSuccessResponse(stored).asJsonString, status))
    stubTail()

    val result = dispatcher.createPartitioning(
      PartitioningCreateRequest(testPartitioningDTO, parents, "author", copyMeasureDefinitions = flag)
    )

    result shouldBe AtumContextDTO(
      partitioning = testPartitioningDTO,
      measures = measures.toSet,
      additionalData = additionalData.map(item => item.key -> item.value).toMap
    )

    // exactly one POST, never a PATCH, never a GET of the child; n parent GETs + POST + measures + additional data
    verify(mockBackend, times(1)).send(isPostPartitioningRequest)
    verify(mockBackend, never()).send(isPatchAncestorsRequest)
    verify(mockBackend, never()).send(isGetPartitioningRequest(testPartitioningDTO))
    verify(mockBackend, times(n + 3)).send(any[Request[Either[String, String], capabilities.WebSockets]])

    val bodies = capturePostBodies()
    bodies should have size 1
    bodies.head.parentPartitioningIds shouldBe (0 until n).map(901L + _)
    bodies.head.partitioning shouldBe testPartitioningDTO
    bodies.head.author shouldBe "author"
    bodies.head.copyMeasureDefinitions shouldBe flag
  }

  for {
    n <- 0 to 3
    (status, statusName) <- Seq((StatusCode.Created, "201 Created"), (StatusCode.Ok, "200 OK"))
    flag <- Seq(false, true)
  } {
    it should s"send exactly one POST for $n parent(s), status $statusName, flag $flag (no child GET, no PATCH)" in {
      runMatrix(n, status, flag)
    }
  }

  it should "propagate HttpException and send no POST when any parent is not found" in {
    val parents = parentsN(2)
    stubParent(parents.head, 901L)
    stubGetPartitioning(parents(1), Response(Left("Not found"): Either[String, String], StatusCode.NotFound))
    stubPostPartitioning(okResp(SingleSuccessResponse(createdPartitioningWithId).asJsonString, StatusCode.Created))
    stubTail()

    an[HttpException] should be thrownBy
      dispatcher.createPartitioning(PartitioningCreateRequest(testPartitioningDTO, parents, "author"))
    verify(mockBackend, never()).send(isPostPartitioningRequest)
    verify(mockBackend, never()).send(isPatchAncestorsRequest)
  }

  it should "POST copyMeasureDefinitions = false for a request built with the Option factory (Some parent)" in {
    val parent = Seq(PartitionDTO("pk1", "pv1"))
    stubParent(parent, 901L)
    stubPostPartitioning(okResp(SingleSuccessResponse(createdPartitioningWithId).asJsonString, StatusCode.Created))
    stubTail()

    dispatcher.createPartitioning(PartitioningCreateRequest(testPartitioningDTO, Some(parent), "author"))

    verify(mockBackend, never()).send(isGetPartitioningRequest(testPartitioningDTO))
    verify(mockBackend, never()).send(isPatchAncestorsRequest)
    val bodies = capturePostBodies()
    bodies should have size 1
    bodies.head.parentPartitioningIds shouldBe Seq(901L)
    bodies.head.copyMeasureDefinitions shouldBe false
  }

  it should "POST copyMeasureDefinitions = false and no parents for a request built with the Option factory (None)" in {
    stubPostPartitioning(okResp(SingleSuccessResponse(createdPartitioningWithId).asJsonString, StatusCode.Created))
    stubTail()

    dispatcher.createPartitioning(PartitioningCreateRequest(testPartitioningDTO, None, "author"))

    verify(mockBackend, never()).send(isGetPartitioningRequest(testPartitioningDTO))
    val bodies = capturePostBodies()
    bodies should have size 1
    bodies.head.parentPartitioningIds shouldBe Seq.empty[Long]
    bodies.head.copyMeasureDefinitions shouldBe false
  }

  private def isPatchAncestorsRequest: Request[Either[String, String], capabilities.WebSockets] =
    argThat(new org.mockito.ArgumentMatcher[Request[Either[String, String], sttp.capabilities.WebSockets]] {
      override def matches(req: Request[Either[String, String], sttp.capabilities.WebSockets]): Boolean =
        req != null && req.method.method == "PATCH" && req.uri.path.mkString.contains("ancestors")
    })

}
package za.co.absa.atum.agent.dispatcher

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import sttp.client3._
import sttp.model.StatusCode
import com.typesafe.config.Config
import sttp.capabilities
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax

class HttpDispatcherUnitTests extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  var mockBackend: SttpBackend[Identity, sttp.capabilities.WebSockets] = _
  var mockConfig: Config = _
  val serverUrl = "http://test-server"

  val testPartitioningDTO: Seq[PartitionDTO] = Seq(PartitionDTO("k", "v"))
  val testPartitioningSubmitDTO = PartitioningSubmitDTO(testPartitioningDTO, None, "author")
  val createdPartitioningWithId = PartitioningWithIdDTO(123L, testPartitioningDTO, "author")
  val measures: Seq[MeasureDTO] = Seq(MeasureDTO("m1", Seq("c1")), MeasureDTO("m2", Seq("c2")))
  val additionalData: Map[String, Option[AdditionalDataItemDTO]] = Map(
    "key1" -> Some(AdditionalDataItemDTO("val1", "author1")),
    "key2" -> None
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
      Response(Right(SingleSuccessResponse(additionalData).asJsonString): Either[String, String], StatusCode.Ok)

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
      Right(SingleSuccessResponse(Map.empty[String, Option[AdditionalDataItemDTO]]).asJsonString): Either[
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
}

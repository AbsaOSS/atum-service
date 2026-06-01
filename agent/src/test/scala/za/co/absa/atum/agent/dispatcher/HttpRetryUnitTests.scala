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

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities
import sttp.client3._
import sttp.model.StatusCode
import za.co.absa.atum.agent.exception.AtumAgentException.HttpException
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax

import java.io.IOException

class HttpRetryUnitTests extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  // ---------------------------------------------------------------------------
  // Shared fixtures
  // ---------------------------------------------------------------------------

  var mockBackend: SttpBackend[Identity, sttp.capabilities.WebSockets] = _
  var mockConfig: Config = _
  val serverUrl = "http://test-server"

  val testPartitioningDTO: Seq[PartitionDTO] = Seq(PartitionDTO("k", "v"))

  override def beforeEach(): Unit = {
    mockBackend = mock(classOf[SttpBackend[Identity, sttp.capabilities.WebSockets]])
    mockConfig = mock(classOf[Config])
    // getString is called for UrlKey during HttpDispatcher construction
    when(mockConfig.getString(any[String])).thenReturn(serverUrl)
    // hasPath is called by HttpRetry.fromConfig; returning false causes Default to be used
    when(mockConfig.hasPath(any[String])).thenReturn(false)
  }

  /** Dispatcher backed by mockConfig (uses HttpRetry.Default: maxRetries=3). */
  def dispatcher: HttpDispatcher = new HttpDispatcher(mockConfig) {
    override private[dispatcher] val backend = mockBackend
  }

  /**
   * Builds a real Typesafe Config with explicit retry settings.
   * Using ConfigFactory avoids mocking hasPath/getInt/getLong interactions.
   * Delays are set to 1 ms by default so tests complete in milliseconds.
   */
  def configWithRetry(
    maxRetries: Int,
    initialDelayMs: Long = 1L,
    maxDelayMs: Long = 50L
  ): Config =
    ConfigFactory.empty()
      .withValue("atum.dispatcher.http.url",
        ConfigValueFactory.fromAnyRef(serverUrl))
      .withValue("atum.dispatcher.http.retry.max-retries",
        ConfigValueFactory.fromAnyRef(maxRetries))
      .withValue("atum.dispatcher.http.retry.initial-delay-ms",
        ConfigValueFactory.fromAnyRef(initialDelayMs))
      .withValue("atum.dispatcher.http.retry.max-delay-ms",
        ConfigValueFactory.fromAnyRef(maxDelayMs))

  /** Dispatcher backed by a real config — use when testing custom retry counts/delays. */
  def dispatcherWithConfig(cfg: Config): HttpDispatcher = new HttpDispatcher(cfg) {
    override private[dispatcher] val backend = mockBackend
  }

  // ---------------------------------------------------------------------------
  // Response helpers
  // ---------------------------------------------------------------------------

  def okPartitioningResponse(id: Long): Response[Either[String, String]] = {
    val dto = PartitioningWithIdDTO(id, testPartitioningDTO, "author")
    Response(
      Right(SingleSuccessResponse(dto).asJsonString): Either[String, String],
      StatusCode.Ok
    )
  }

  val serverErrorResponse: Response[Either[String, String]] =
    Response(Left("Internal Server Error"): Either[String, String], StatusCode.InternalServerError)

  val serviceUnavailableResponse: Response[Either[String, String]] =
    Response(Left("Service Unavailable"): Either[String, String], StatusCode.ServiceUnavailable)

  val badRequestResponse: Response[Either[String, String]] =
    Response(Left("Bad Request"): Either[String, String], StatusCode.BadRequest)

  val notFoundResponse: Response[Either[String, String]] =
    Response(Left("Not Found"): Either[String, String], StatusCode.NotFound)

  // ---------------------------------------------------------------------------
  // withRetry — 5xx retry behaviour
  // ---------------------------------------------------------------------------

  "withRetry" should "succeed on first attempt without issuing any retry" in {
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenReturn(okPartitioningResponse(42L))

    val result = dispatcher.getPartitioningId(testPartitioningDTO)

    result shouldBe 42L
    verify(mockBackend, times(1)).send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

  it should "retry on HTTP 503 and succeed on the second attempt" in {
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenReturn(serviceUnavailableResponse)  // attempt 1: 503
      .thenReturn(okPartitioningResponse(99L)) // attempt 2: 200

    val d = dispatcherWithConfig(configWithRetry(maxRetries = 1))
    val result = d.getPartitioningId(testPartitioningDTO)

    result shouldBe 99L
    verify(mockBackend, times(2)).send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

  it should "retry on HTTP 500 and succeed on the third attempt" in {
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenReturn(serverErrorResponse)         // attempt 1: 500
      .thenReturn(serverErrorResponse)         // attempt 2: 500
      .thenReturn(okPartitioningResponse(7L))  // attempt 3: 200

    val d = dispatcherWithConfig(configWithRetry(maxRetries = 2))
    val result = d.getPartitioningId(testPartitioningDTO)

    result shouldBe 7L
    verify(mockBackend, times(3)).send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

  it should "throw HttpException after exhausting retries on persistent 5xx" in {
    // Always returns 500 — retries = 2 means 3 total send() calls (1 + 2 retries)
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenReturn(serverErrorResponse)

    val d = dispatcherWithConfig(configWithRetry(maxRetries = 2))

    val thrown = intercept[HttpException] {
      d.getPartitioningId(testPartitioningDTO)
    }

    thrown.statusCode shouldBe 500
    verify(mockBackend, times(3)).send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

  // ---------------------------------------------------------------------------
  // withRetry — 4xx NO-retry behaviour
  // ---------------------------------------------------------------------------

  it should "NOT retry on HTTP 400 (client error) and throw HttpException immediately" in {
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenReturn(badRequestResponse)

    val d = dispatcherWithConfig(configWithRetry(maxRetries = 3))

    an[HttpException] should be thrownBy d.getPartitioningId(testPartitioningDTO)
    // Exactly one send() call — no retry
    verify(mockBackend, times(1)).send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

  it should "NOT retry on HTTP 404 (not found) and return None from getPartitioning" in {
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenReturn(notFoundResponse)

    val d = dispatcherWithConfig(configWithRetry(maxRetries = 3))
    val result = d.getPartitioning(testPartitioningDTO)

    result shouldBe None
    // Exactly one send() call — no retry
    verify(mockBackend, times(1)).send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

  // ---------------------------------------------------------------------------
  // withRetry — IOException retry behaviour (RETRY-01)
  // ---------------------------------------------------------------------------

  it should "retry on IOException and succeed on the second attempt" in {
    val ioException = new IOException("connection reset")
    // thenThrow(checked IOException) fails Mockito's signature check; use thenAnswer instead
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenAnswer(new org.mockito.stubbing.Answer[Response[Either[String, String]]] {
        override def answer(invocation: org.mockito.invocation.InvocationOnMock): Response[Either[String, String]] =
          throw ioException
      })
      .thenReturn(okPartitioningResponse(55L)) // attempt 2: 200

    val d = dispatcherWithConfig(configWithRetry(maxRetries = 1))
    val result = d.getPartitioningId(testPartitioningDTO)

    result shouldBe 55L
    verify(mockBackend, times(2)).send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

  it should "throw IOException after exhausting retries on persistent IOException" in {
    val ioException = new IOException("connection refused")
    // thenThrow(checked IOException) fails Mockito's signature check; use thenAnswer instead
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenAnswer(new org.mockito.stubbing.Answer[Response[Either[String, String]]] {
        override def answer(invocation: org.mockito.invocation.InvocationOnMock): Response[Either[String, String]] =
          throw ioException
      })

    val d = dispatcherWithConfig(configWithRetry(maxRetries = 2))

    // 1 original + 2 retries = 3 send() calls, then IOException is rethrown
    an[IOException] should be thrownBy d.getPartitioningId(testPartitioningDTO)
    verify(mockBackend, times(3)).send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

  // ---------------------------------------------------------------------------
  // HttpRetry backward-compatibility and defaults
  // ---------------------------------------------------------------------------

  it should "exhaust exactly HttpRetry.Default.maxRetries + 1 attempts on persistent 5xx" in {
    // Verify that Default.maxRetries=3 produces exactly 4 total send() calls
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenReturn(serverErrorResponse)

    val d = dispatcherWithConfig(configWithRetry(maxRetries = HttpRetry.Default.maxRetries))
    an[HttpException] should be thrownBy d.getPartitioningId(testPartitioningDTO)
    verify(mockBackend, times(HttpRetry.Default.maxRetries + 1))
      .send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

  it should "not throw ConfigException when constructing HttpDispatcher from a config with no retry block" in {
    // A minimal real config with only the URL key — simulates a legacy consumer config
    val legacyConfig = ConfigFactory.empty()
      .withValue("atum.dispatcher.http.url", ConfigValueFactory.fromAnyRef(serverUrl))

    // Construction must succeed without ConfigException
    noException should be thrownBy {
      new HttpDispatcher(legacyConfig) {
        override private[dispatcher] val backend = mockBackend
      }
    }
  }

  // ---------------------------------------------------------------------------
  // maxRetries=0 edge case
  // ---------------------------------------------------------------------------

  it should "not retry at all when maxRetries is 0" in {
    when(mockBackend.send(any[Request[Either[String, String], capabilities.WebSockets]]))
      .thenReturn(serverErrorResponse)

    val d = dispatcherWithConfig(configWithRetry(maxRetries = 0))

    an[HttpException] should be thrownBy d.getPartitioningId(testPartitioningDTO)
    // maxRetries=0 means no retries: exactly 1 send() call
    verify(mockBackend, times(1)).send(any[Request[Either[String, String], capabilities.WebSockets]])
  }

}

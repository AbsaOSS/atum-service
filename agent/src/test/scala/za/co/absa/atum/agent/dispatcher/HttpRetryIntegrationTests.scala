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

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.exception.AtumAgentException.HttpException
import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningWithIdDTO}
import za.co.absa.atum.model.envelopes.SuccessResponse.SingleSuccessResponse
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 *  End-to-end integration test for the HTTP dispatcher retry mechanism.
 *
 *  Unlike [[HttpRetryUnitTests]] (which mocks the sttp backend), this test drives the real
 *  [[HttpDispatcher]] + [[HttpRetry]] + OkHttp backend over a real TCP socket against a tiny stub HTTP
 *  server (JDK `com.sun.net.httpserver.HttpServer`, no extra dependencies). The stub returns a chosen
 *  number of `503` responses before recovering with `200`, which is exactly the transient-failure
 *  scenario that a healthy atum-server can never reproduce on the happy path.
 *
 *  It is intentionally self-contained (no atum-server, no Postgres), so it runs in the standard agent
 *  CI job via the `*IntegrationTests` naming convention.
 */
class HttpRetryIntegrationTests extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private var server: HttpServer = _
  private val requestCount = new AtomicInteger(0)

  private val partitioning: Seq[PartitionDTO] = Seq(PartitionDTO("k", "v"))

  // Build the exact success envelope the dispatcher expects instead of hand-writing JSON.
  private val okBody: String =
    SingleSuccessResponse(PartitioningWithIdDTO(42L, partitioning, "author")).asJsonString

  /**
   *  Starts a stub HTTP server that serves the `getPartitioningId` GET endpoint and returns `503` for
   *  the first `failTimes` requests, then `200` with a valid partitioning payload. The request counter
   *  is reset so each test observes only its own traffic.
   *
   *  @param failTimes number of leading requests answered with `503` (use `Int.MaxValue` to always fail).
   */
  private def startServer(failTimes: Int): Unit = {
    requestCount.set(0)
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    server.createContext(
      "/api/v2/partitionings",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val attempt = requestCount.incrementAndGet()
          val (status, body) =
            if (attempt <= failTimes) (503, "service unavailable")
            else (200, okBody)

          val bytes = body.getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(status, bytes.length.toLong)
          val os = exchange.getResponseBody
          try os.write(bytes)
          finally os.close()
        }
      }
    )
    server.start()
  }

  private def baseUrl: String = s"http://localhost:${server.getAddress.getPort}"

  /** Real HttpDispatcher pointing at the stub, with tiny delays so the test finishes in milliseconds. */
  private def dispatcher(maxRetries: Int): HttpDispatcher = {
    val config: Config = ConfigFactory
      .empty()
      .withValue("atum.dispatcher.http.url", ConfigValueFactory.fromAnyRef(baseUrl))
      .withValue("atum.dispatcher.http.retry.max-retries", ConfigValueFactory.fromAnyRef(maxRetries))
      .withValue("atum.dispatcher.http.retry.initial-delay-ms", ConfigValueFactory.fromAnyRef(1))
      .withValue("atum.dispatcher.http.retry.max-delay-ms", ConfigValueFactory.fromAnyRef(5))
    new HttpDispatcher(config)
  }

  override def afterEach(): Unit = {
    if (server != null) {
      server.stop(0)
      server = null
    }
  }

  "HttpDispatcher" should "retry on HTTP 503 and succeed once the stub server recovers" in {
    startServer(failTimes = 2) // 503, 503, then 200

    val result = dispatcher(maxRetries = 3).getPartitioningId(partitioning)

    result shouldBe 42L
    requestCount.get() shouldBe 3 // 2 failed attempts + 1 successful attempt => 2 retries happened
  }

  it should "throw HttpException after exhausting retries on a persistently failing server" in {
    startServer(failTimes = Int.MaxValue) // always 503

    an[HttpException] should be thrownBy dispatcher(maxRetries = 2).getPartitioningId(partitioning)

    requestCount.get() shouldBe 3 // 1 initial attempt + 2 retries
  }

  it should "not retry when maxRetries is 0 and fail on the first 503" in {
    startServer(failTimes = Int.MaxValue) // always 503

    an[HttpException] should be thrownBy dispatcher(maxRetries = 0).getPartitioningId(partitioning)

    requestCount.get() shouldBe 1 // no retries at all
  }
}

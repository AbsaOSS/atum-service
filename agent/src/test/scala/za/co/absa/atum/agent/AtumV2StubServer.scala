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

package za.co.absa.atum.agent

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import za.co.absa.atum.model.dto.{
  AdditionalDataItemV2DTO,
  MeasureDTO,
  PartitioningDTO,
  PartitioningSubmitV2DTO,
  PartitioningWithIdDTO
}
import za.co.absa.atum.model.envelopes.SuccessResponse.{MultiSuccessResponse, SingleSuccessResponse}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.{JsonDeserializationSyntax, JsonSerializationSyntax}

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Try

/**
 *  One request seen by [[AtumV2StubServer]].
 *
 *  @param method         HTTP method
 *  @param path           request path (decoded, without the query)
 *  @param query          raw (still URL-encoded) query string, if any
 *  @param body           request body (UTF-8), if any
 *  @param answeredStatus status code the stub answered with
 */
final case class RecordedRequest(
  method: String,
  path: String,
  query: Option[String],
  body: Option[String],
  answeredStatus: Int
)

/**
 *  Recording stub of the Phase 9 server contract for `/api/v2/partitionings`, based on the JDK
 *  `com.sun.net.httpserver.HttpServer` (loopback, ephemeral port, no extra dependencies):
 *
 *   - `GET  /api/v2/partitionings?partitioning=<base64 json>`: 200 with the partitioning and its id, or 404;
 *   - `POST /api/v2/partitionings`: 201 (created) or 200 (already existing) with the same body type and a `Location`
 *     header; an armed failure (see `failNextPostWith`) is answered instead;
 *   - `GET  /api/v2/partitionings/{id}/measures` and `.../additional-data`: fixed small payloads;
 *   - `PATCH` of anything: recorded and answered 204 (the agent must never send it any more);
 *   - everything else: recorded and answered 404.
 *
 *  Every handled request is recorded (before the response is sent) in arrival order.
 */
class AtumV2StubServer {
  import AtumV2StubServer._

  private val lock = new Object
  private var server: HttpServer = _
  // key = JSON string of the partitioning (what the base64 query parameter decodes to)
  private var registry: Map[String, (Long, PartitioningDTO, String)] = Map.empty
  private var nextId: Long = FirstGeneratedId
  private var armedPostFailure: Option[Int] = None
  private var recorded: Vector[RecordedRequest] = Vector.empty

  def start(): Unit = {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
    server.createContext(
      BasePath,
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = handleExchange(exchange)
      }
    )
    server.start()
  }

  def stop(): Unit = {
    if (server != null) {
      server.stop(0)
      server = null
    }
  }

  def baseUrl: String = s"http://localhost:${server.getAddress.getPort}"

  /** Pre-seeds a known partitioning (e.g. a parent) under the given id. */
  def register(partitioning: PartitioningDTO, id: Long): Unit = lock.synchronized {
    registry = registry + (partitioning.asJsonString -> ((id, partitioning, StubAuthor)))
  }

  /** The id under which the partitioning is currently known, if any. */
  def idOf(partitioning: PartitioningDTO): Option[Long] = lock.synchronized {
    registry.get(partitioning.asJsonString).map(_._1)
  }

  /** Arms a failure status (e.g. 409) answered by the next POST only. */
  def failNextPostWith(status: Int): Unit = lock.synchronized {
    armedPostFailure = Some(status)
  }

  /** Snapshot of all requests seen so far, in arrival order. */
  def requests: List[RecordedRequest] = lock.synchronized(recorded.toList)

  def clearRequests(): Unit = lock.synchronized {
    recorded = Vector.empty
  }

  private def handleExchange(exchange: HttpExchange): Unit = {
    try {
      val method = exchange.getRequestMethod
      val uri = exchange.getRequestURI
      val path = uri.getPath
      val body = Some(readBody(exchange)).filter(_.nonEmpty)

      val response = Try(route(method, path, Option(uri.getQuery), body)).getOrElse(
        StubResponse(500, "stub failure")
      )

      lock.synchronized {
        recorded = recorded :+ RecordedRequest(method, path, Option(uri.getRawQuery), body, response.status)
      }

      response.headers.foreach { case (name, value) => exchange.getResponseHeaders.add(name, value) }
      val bytes = response.body.getBytes(StandardCharsets.UTF_8)
      if (bytes.isEmpty) {
        exchange.sendResponseHeaders(response.status, -1L)
      } else {
        exchange.sendResponseHeaders(response.status, bytes.length.toLong)
        exchange.getResponseBody.write(bytes)
      }
    } finally {
      exchange.close()
    }
  }

  private def route(method: String, path: String, decodedQuery: Option[String], body: Option[String]): StubResponse = {
    (method, path) match {
      case ("PATCH", _) => StubResponse(204, "")
      case ("GET", BasePath) => lookup(decodedQuery)
      case ("POST", BasePath) => create(body)
      case ("GET", MeasuresPath(_)) =>
        StubResponse(200, MultiSuccessResponse(Seq(MeasureDTO("count", Seq("*")))).asJsonString)
      case ("GET", AdditionalDataPath(_)) =>
        val items = Seq(AdditionalDataItemV2DTO("k", Some("v"), StubAuthor))
        StubResponse(200, MultiSuccessResponse(items).asJsonString)
      case _ => StubResponse(404, "not found")
    }
  }

  private def lookup(decodedQuery: Option[String]): StubResponse = {
    val key = decodedQuery
      .filter(_.startsWith(PartitioningParam))
      .map(_.stripPrefix(PartitioningParam))
      .flatMap(encoded => Try(new String(Base64.getUrlDecoder.decode(encoded), StandardCharsets.UTF_8)).toOption)

    val known = key.flatMap(k => lock.synchronized(registry.get(k)))
    known match {
      case Some((id, partitioning, author)) =>
        StubResponse(200, SingleSuccessResponse(PartitioningWithIdDTO(id, partitioning, author)).asJsonString)
      case None => StubResponse(404, "partitioning not found")
    }
  }

  private def create(body: Option[String]): StubResponse = {
    val dto = Try(body.getOrElse("").as[PartitioningSubmitV2DTO]).toOption
    lock.synchronized {
      (dto, armedPostFailure) match {
        case (None, _) => StubResponse(400, "undecodable body")
        case (Some(_), Some(status)) =>
          armedPostFailure = None
          StubResponse(status, "armed failure")
        case (Some(submit), None) =>
          val key = submit.partitioning.asJsonString
          registry.get(key) match {
            case Some((id, partitioning, author)) =>
              StubResponse(200, createdBody(id, partitioning, author), Map("Location" -> s"$BasePath/$id"))
            case None =>
              val id = nextId
              nextId += 1
              registry = registry + (key -> ((id, submit.partitioning, submit.author)))
              StubResponse(
                201,
                createdBody(id, submit.partitioning, submit.author),
                Map("Location" -> s"$BasePath/$id")
              )
          }
      }
    }
  }

  private def createdBody(id: Long, partitioning: PartitioningDTO, author: String): String =
    SingleSuccessResponse(PartitioningWithIdDTO(id, partitioning, author)).asJsonString

  private def readBody(exchange: HttpExchange): String = {
    val in = exchange.getRequestBody
    val out = new ByteArrayOutputStream()
    val buffer = new Array[Byte](4096)
    var read = in.read(buffer)
    while (read != -1) {
      out.write(buffer, 0, read)
      read = in.read(buffer)
    }
    new String(out.toByteArray, StandardCharsets.UTF_8)
  }
}

object AtumV2StubServer {
  val BasePath = "/api/v2/partitionings"
  val StubAuthor = "stub-author"

  private final case class StubResponse(status: Int, body: String, headers: Map[String, String] = Map.empty)

  private val FirstGeneratedId = 1000L
  private val PartitioningParam = "partitioning="
  private val MeasuresPath = s"""$BasePath/(\\d+)/measures""".r
  private val AdditionalDataPath = s"""$BasePath/(\\d+)/additional-data""".r
}

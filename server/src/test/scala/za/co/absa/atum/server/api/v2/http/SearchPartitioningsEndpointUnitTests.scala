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

import io.circe.parser.{decode, parse}
import io.circe.syntax._
import org.mockito.Mockito.{mock, verify, verifyNoInteractions, verifyNoMoreInteractions, when}
import sttp.client3.UriContext
import sttp.model.{StatusCode, Uri}
import za.co.absa.atum.model.dto.PartitioningWithIdDTO
import za.co.absa.atum.model.envelopes.SuccessResponse.{PaginatedResponse, SingleSuccessResponse}
import za.co.absa.atum.model.envelopes.{InternalServerErrorResponse, Pagination}
import za.co.absa.atum.server.api.PartitioningSearchTestData
import za.co.absa.atum.server.api.v2.controller.PartitioningController
import zio.test._
import zio.{Scope, ZIO}

import java.time.ZonedDateTime

object SearchPartitioningsEndpointUnitTests extends ZIOSpecDefault with PartitioningSearchTestData {
  private val searchUri = uri"http://localhost/api/v2/partitionings/search"
  private val encodedKeys = encodeSearchJson(searchKeys.asJson.noSpaces)

  private case class ValidSearch(
    name: String,
    params: Seq[(String, String)],
    limit: Int = 10,
    offset: Long = 0L,
    keys: Option[Map[String, String]] = None,
    from: Option[ZonedDateTime] = None,
    to: Option[ZonedDateTime] = None,
    data: Seq[PartitioningWithIdDTO] = searchPartitionings,
    hasMore: Boolean = false
  )

  private val validSearches = Seq(
    ValidSearch("key-only search decodes Unicode and empty string values with defaults",
      Seq("keys" -> encodedKeys), keys = Some(searchKeys)),
    ValidSearch("time-only bounded search",
      Seq("from" -> searchFrom.toString, "to" -> searchTo.toString), from = Some(searchFrom), to = Some(searchTo)),
    ValidSearch("combined search forwards custom limit and nonzero offset",
      Seq("keys" -> encodedKeys, "from" -> searchFrom.toString, "to" -> searchTo.toString,
        "limit" -> "1", "offset" -> "2"),
      limit = 1, offset = 2L, keys = Some(searchKeys), from = Some(searchFrom), to = Some(searchTo),
      data = searchPartitionings.take(1), hasMore = true),
    ValidSearch("maximum limit and offset beyond Int range are accepted",
      Seq("keys" -> encodedKeys, "limit" -> "1000", "offset" -> searchOffset.toString),
      limit = 1000, offset = searchOffset, keys = Some(searchKeys)),
    ValidSearch("empty keys with a complete time window are accepted",
      Seq("keys" -> encodeSearchJson("{}"), "from" -> searchFrom.toString, "to" -> searchTo.toString),
      keys = Some(Map.empty[String, String]), from = Some(searchFrom), to = Some(searchTo)),
    ValidSearch("a 365-day time window is accepted",
      Seq("from" -> "2026-06-01T00:00:00Z", "to" -> "2027-06-01T00:00:00Z"),
      from = Some(ZonedDateTime.parse("2026-06-01T00:00:00Z")),
      to = Some(ZonedDateTime.parse("2027-06-01T00:00:00Z"))),
    ValidSearch("valid instant ordering is accepted even when local date ordering is reversed",
      Seq("from" -> "2026-06-02T00:30:00+02:00", "to" -> "2026-06-01T23:45:00Z"),
      from = Some(ZonedDateTime.parse("2026-06-02T00:30:00+02:00")),
      to = Some(ZonedDateTime.parse("2026-06-01T23:45:00Z"))),
    ValidSearch("empty matches return 200 with no more results",
      Seq("keys" -> encodedKeys, "limit" -> "2", "offset" -> "99"),
      limit = 2, offset = 99L, keys = Some(searchKeys), data = Seq.empty)
  )

  private val invalidKeys = Seq(
    "empty object alone" -> encodeSearchJson("{}"),
    "number value" -> encodeSearchJson("""{"key":1}"""),
    "boolean value" -> encodeSearchJson("""{"key":true}"""),
    "null value" -> encodeSearchJson("""{"key":null}"""),
    "NUL in a key" -> encodeSearchJson(Map("\u0000" -> "value").asJson.noSpaces),
    "NUL in a value" -> encodeSearchJson(Map("key" -> "\u0000").asJson.noSpaces),
    "nested object" -> encodeSearchJson("""{"key":{"child":"value"}}"""),
    "array value" -> encodeSearchJson("""{"key":["value"]}"""),
    "top-level array" -> encodeSearchJson("""["value"]"""),
    "top-level null" -> encodeSearchJson("null"),
    "top-level string" -> encodeSearchJson(""""value""""),
    "malformed JSON" -> encodeSearchJson("""{"key":"""),
    "invalid base64 alphabet" -> "*not-base64*",
    "truncated base64" -> "a",
    "empty encoded value" -> ""
  )

  private val invalidQueries: Seq[(String, Seq[(String, String)])] =
    Seq(
      "missing filters" -> Seq.empty[(String, String)],
      "pagination without filters" -> Seq("limit" -> "2", "offset" -> "1")
    ) ++
      invalidKeys.map { case (name, encoded) => name -> Seq("keys" -> encoded) } ++
      Seq(
        "invalid from" -> Seq("from" -> "not-a-date"),
        "invalid to" -> Seq("to" -> "2026-99-01T00:00:00Z"),
        "missing date offset" -> Seq("from" -> "2026-06-01T00:00:00"),
        "from without to" -> Seq("from" -> searchFrom.toString),
        "to without from" -> Seq("to" -> searchTo.toString),
        "equal time bounds" -> Seq("from" -> searchFrom.toString, "to" -> searchFrom.toString),
        "equal instants in different timezones" ->
          Seq("from" -> "2026-06-01T02:00:00+02:00", "to" -> "2026-06-01T00:00:00Z"),
        "reversed bounds" -> Seq("from" -> searchTo.toString, "to" -> searchFrom.toString),
        "locally ordered but instant-reversed bounds" ->
          Seq("from" -> "2026-06-01T00:00:00-04:00", "to" -> "2026-06-01T02:00:00+02:00"),
        "window longer than 365 days" ->
          Seq("from" -> "2026-06-01T00:00:00Z", "to" -> "2027-06-02T00:00:00Z")
      ).map { case (name, params) => name -> (Seq("keys" -> encodedKeys) ++ params) } ++
      Seq(
        "zero limit" -> ("limit" -> "0"),
        "negative limit" -> ("limit" -> "-1"),
        "excessive limit" -> ("limit" -> "1001"),
        "non-numeric limit" -> ("limit" -> "many"),
        "overflowing limit" -> ("limit" -> "2147483648"),
        "negative offset" -> ("offset" -> "-1"),
        "non-numeric offset" -> ("offset" -> "page"),
        "overflowing offset" -> ("offset" -> "9223372036854775808")
      ).map { case (name, param) => name -> Seq("keys" -> encodedKeys, param) }

  private def withParams(params: Seq[(String, String)]): Uri =
    params.foldLeft(searchUri) { case (uri, (name, value)) => uri.addParam(name, value) }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SearchPartitioningsEndpointSuite")(
    suite("Valid searches")(validSearches.map { scenario =>
      test(scenario.name) {
        val controller = mock(classOf[PartitioningController])
        val expected = PaginatedResponse(
          scenario.data, Pagination(scenario.limit, scenario.offset, scenario.hasMore), searchRequestId
        )
        when(controller.searchPartitionings(
          scenario.limit, scenario.offset, scenario.keys, scenario.from, scenario.to
        )).thenReturn(ZIO.succeed(expected))

        for {
          response <- PartitioningSearchTestSupport.get(withParams(scenario.params), controller)
          _ <- ZIO.attempt {
            verify(controller).searchPartitionings(
              scenario.limit, scenario.offset, scenario.keys, scenario.from, scenario.to
            )
            verifyNoMoreInteractions(controller)
          }
          json <- ZIO.fromEither(parse(response._2))
          itemFields = json.hcursor.downField("data").focus.toSeq.flatMap(_.asArray.toSeq.flatten)
            .flatMap(_.asObject).map(_.keys.toSet)
        } yield assertTrue(
          response._1 == StatusCode.Ok,
          decode[PaginatedResponse[PartitioningWithIdDTO]](response._2) == Right(expected),
          json.asObject.map(_.keys.toSet).contains(Set("data", "pagination", "requestId")),
          itemFields.forall(_ == Set("id", "partitioning", "author"))
        )
      }
    }: _*),
    suite("Invalid searches")(invalidQueries.map { case (name, params) =>
      test(s"Rejects $name before invoking the controller") {
        val controller = mock(classOf[PartitioningController])
        for {
          response <- PartitioningSearchTestSupport.get(withParams(params), controller)
          _ <- ZIO.attempt(verifyNoInteractions(controller))
        } yield assertTrue(response._1 == StatusCode.BadRequest)
      }
    }: _*),
    test("Surfaces an upstream failure as an InternalServerErrorResponse") {
      val controller = mock(classOf[PartitioningController])
      val error = InternalServerErrorResponse("search unavailable", searchRequestId)
      when(controller.searchPartitionings(10, 0L, Some(searchKeys), None, None)).thenReturn(ZIO.fail(error))
      for {
        response <- PartitioningSearchTestSupport.get(withParams(Seq("keys" -> encodedKeys)), controller)
      } yield assertTrue(
        response._1 == StatusCode.InternalServerError,
        decode[InternalServerErrorResponse](response._2) == Right(error)
      )
    },
    test("Published OpenAPI documentation includes the search endpoint") {
      val controller = mock(classOf[PartitioningController])
      for {
        response <- PartitioningSearchTestSupport.get(uri"http://localhost/docs/docs.yaml", controller)
        _ <- ZIO.attempt(verifyNoInteractions(controller))
      } yield assertTrue(
        response._1 == StatusCode.Ok,
        response._2.contains("/api/v2/partitionings/search:")
      )
    },
    test("Registered search route does not shadow or get consumed by exact and numeric-ID routes") {
      val controller = mock(classOf[PartitioningController])
      val partitioning = searchPartitionings.head
      val encodedPartitioning = encodeSearchJson(partitioning.partitioning.asJson.noSpaces)
      val single = SingleSuccessResponse(partitioning, searchRequestId)
      val page = PaginatedResponse(Seq(partitioning), Pagination(10, 0L, hasMore = false), searchRequestId)
      when(controller.searchPartitionings(10, 0L, Some(searchKeys), None, None)).thenReturn(ZIO.succeed(page))
      when(controller.getPartitioning(encodedPartitioning)).thenReturn(ZIO.succeed(single))
      when(controller.getPartitioningById(partitioning.id)).thenReturn(ZIO.succeed(single))
      for {
        search <- PartitioningSearchTestSupport.get(withParams(Seq("keys" -> encodedKeys)), controller)
        exact <- PartitioningSearchTestSupport.get(
          uri"http://localhost/api/v2/partitionings".addParam("partitioning", encodedPartitioning), controller
        )
        byId <- PartitioningSearchTestSupport.get(
          uri"http://localhost/api/v2/partitionings/${partitioning.id}", controller
        )
        _ <- ZIO.attempt {
          verify(controller).searchPartitionings(10, 0L, Some(searchKeys), None, None)
          verify(controller).getPartitioning(encodedPartitioning)
          verify(controller).getPartitioningById(partitioning.id)
          verifyNoMoreInteractions(controller)
        }
      } yield assertTrue(
        search._1 == StatusCode.Ok,
        exact._1 == StatusCode.Ok,
        byId._1 == StatusCode.Ok,
        decode[PaginatedResponse[PartitioningWithIdDTO]](search._2) == Right(page),
        decode[SingleSuccessResponse[PartitioningWithIdDTO]](exact._2) == Right(single),
        decode[SingleSuccessResponse[PartitioningWithIdDTO]](byId._2) == Right(single)
      )
    }
  )
}

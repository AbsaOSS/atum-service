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

package za.co.absa.atum.reader.requests

import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.model.utils.JsonSyntaxExtensions._

import java.time.ZonedDateTime

class CheckpointFilterUnitTests extends AnyFunSuiteLike {

  test("An empty filter adds no query params") {
    assert(CheckpointFilter.empty.toQueryParams.isEmpty)
  }

  test("Single-value properties are sent in the single-value format understood by older servers too") {
    val filter = CheckpointFilter(properties = Map("executionID" -> Set("019f8981-7868-79fc-81d3-8143a4706f8a")))
    // base64url-encoded {"executionID":"019f8981-7868-79fc-81d3-8143a4706f8a"}
    assert(
      filter.toQueryParams == Map(
        "checkpoint-properties" -> "eyJleGVjdXRpb25JRCI6IjAxOWY4OTgxLTc4NjgtNzlmYy04MWQzLTgxNDNhNDcwNmY4YSJ9"
      )
    )
  }

  test("Multi-value properties are sent as arrays of values, all properties in the same format") {
    val filter = CheckpointFilter(properties = Map("executionID" -> Set("b", "a"), "env" -> Set("prod")))
    val encoded = filter.toQueryParams("checkpoint-properties")
    assert(
      encoded.fromBase64As[Map[String, Seq[String]]] == Right(Map("executionID" -> Seq("a", "b"), "env" -> Seq("prod")))
    )
  }

  test("The time window is sent as UTC instants and all filter parts are combined") {
    val filter = CheckpointFilter(
      name = Some("checkpoint name"),
      from = Some(ZonedDateTime.parse("2026-06-01T02:00:00+02:00[Europe/Budapest]")),
      to = Some(ZonedDateTime.parse("2026-08-01T00:00:00.5Z")),
      latestFirst = Some(false)
    )
    assert(
      filter.toQueryParams == Map(
        "checkpoint-name" -> "checkpoint name",
        "from" -> "2026-06-01T00:00:00Z",
        "to" -> "2026-08-01T00:00:00.500Z",
        "latest-first" -> "false"
      )
    )
  }

}

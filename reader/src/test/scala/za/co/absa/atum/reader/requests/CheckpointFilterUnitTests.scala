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

  test("Name and properties are combined") {
    val filter = CheckpointFilter(name = Some("checkpoint name"), properties = Map("env" -> Set("prod")))
    assert(
      filter.toQueryParams == Map(
        "checkpoint-name" -> "checkpoint name",
        // base64url-encoded {"env":"prod"}
        "checkpoint-properties" -> "eyJlbnYiOiJwcm9kIn0="
      )
    )
  }

  test("A property without any value is rejected, as it would match no checkpoint") {
    val exception = intercept[IllegalArgumentException] {
      CheckpointFilter(properties = Map("executionID" -> Set.empty[String], "env" -> Set("prod")))
    }
    assert(exception.getMessage.contains("executionID"))
    assert(!exception.getMessage.contains("env"))
  }

}

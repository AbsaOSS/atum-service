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

package za.co.absa.atum.server.api.database.lineage.functions

import io.circe.parser.parse
import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.model.dto.{ChildPartitioningDTO, PartitionDTO}
import za.co.absa.atum.server.api.database.lineage.functions.GetDirectChildren.GetDirectChildrenResult

import java.time.ZonedDateTime

class GetDirectChildrenResultUnitTests extends AnyFunSuiteLike {

  private val now = ZonedDateTime.parse("2026-11-12T20:00:00Z")

  private val validJson = parse(
    """{
      |  "version": 1,
      |  "keys": ["keyX", "keyY"],
      |  "keysToValuesMap": {
      |    "keyX": "valX",
      |    "keyY": "valY"
      |  }
      |}""".stripMargin
  ).getOrElse(fail("Invalid JSON"))

  private val invalidJson = parse(
    """{
      |  "version": "not-an-int",
      |  "keys": 123
      |}""".stripMargin
  ).getOrElse(fail("Invalid JSON"))

  test("toChildPartitioningDTO converts valid JSON into ChildPartitioningDTO") {
    val result = GetDirectChildrenResult(42L, validJson, "author1", "linker1", now)
    val dtoEither = result.toChildPartitioningDTO

    assert(dtoEither.isRight)
    val dto = dtoEither.toOption.get
    assert(dto == ChildPartitioningDTO(
      id = 42L,
      partitioning = Seq(PartitionDTO("keyX", "valX"), PartitionDTO("keyY", "valY")),
      author = "author1",
      linkedBy = Some("linker1"),
      linkedAt = Some(now)
    ))
  }

  test("toChildPartitioningDTO fails on invalid JSON") {
    val result = GetDirectChildrenResult(42L, invalidJson, "author1", "linker1", now)
    val dtoEither = result.toChildPartitioningDTO

    assert(dtoEither.isLeft)
  }

  test("resultsToChildPartitioningDTOs converts sequence of valid results") {
    val r1 = GetDirectChildrenResult(1L, validJson, "a1", "l1", now)
    val r2 = GetDirectChildrenResult(2L, validJson, "a2", "l2", now)

    val dtosEither = GetDirectChildrenResult.resultsToChildPartitioningDTOs(Seq(r1, r2))
    assert(dtosEither.isRight)
    assert(dtosEither.toOption.get.size == 2)
  }

  test("resultsToChildPartitioningDTOs fails when any result has invalid JSON") {
    val r1 = GetDirectChildrenResult(1L, validJson, "a1", "l1", now)
    val r2 = GetDirectChildrenResult(2L, invalidJson, "a2", "l2", now)

    val dtosEither = GetDirectChildrenResult.resultsToChildPartitioningDTOs(Seq(r1, r2))
    assert(dtosEither.isLeft)
  }

}

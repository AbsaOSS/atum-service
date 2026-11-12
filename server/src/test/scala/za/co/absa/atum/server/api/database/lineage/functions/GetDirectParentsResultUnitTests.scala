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
import za.co.absa.atum.model.dto.{ParentPartitioningDTO, PartitionDTO}
import za.co.absa.atum.server.api.database.lineage.functions.GetDirectParents.GetDirectParentsResult

import java.time.ZonedDateTime

class GetDirectParentsResultUnitTests extends AnyFunSuiteLike {

  private val now = ZonedDateTime.parse("2026-11-12T20:00:00Z")

  private val validJson = parse(
    """{
      |  "version": 1,
      |  "keys": ["keyA", "keyB"],
      |  "keysToValuesMap": {
      |    "keyA": "valA",
      |    "keyB": "valB"
      |  }
      |}""".stripMargin
  ).getOrElse(fail("Invalid JSON"))

  private val invalidJson = parse(
    """{
      |  "version": "not-an-int",
      |  "keys": 123
      |}""".stripMargin
  ).getOrElse(fail("Invalid JSON"))

  test("toParentPartitioningDTO converts valid JSON into ParentPartitioningDTO") {
    val result = GetDirectParentsResult(42L, validJson, "author1", "linker1", now)
    val dtoEither = result.toParentPartitioningDTO

    assert(dtoEither.isRight)
    val dto = dtoEither.toOption.get
    assert(dto == ParentPartitioningDTO(
      id = 42L,
      partitioning = Seq(PartitionDTO("keyA", "valA"), PartitionDTO("keyB", "valB")),
      author = "author1",
      linkedBy = Some("linker1"),
      linkedAt = Some(now)
    ))
  }

  test("toParentPartitioningDTO fails on invalid JSON") {
    val result = GetDirectParentsResult(42L, invalidJson, "author1", "linker1", now)
    val dtoEither = result.toParentPartitioningDTO

    assert(dtoEither.isLeft)
  }

  test("resultsToParentPartitioningDTOs converts sequence of valid results") {
    val r1 = GetDirectParentsResult(1L, validJson, "a1", "l1", now)
    val r2 = GetDirectParentsResult(2L, validJson, "a2", "l2", now)

    val dtosEither = GetDirectParentsResult.resultsToParentPartitioningDTOs(Seq(r1, r2))
    assert(dtosEither.isRight)
    assert(dtosEither.toOption.get.size == 2)
  }

  test("resultsToParentPartitioningDTOs fails when any result has invalid JSON") {
    val r1 = GetDirectParentsResult(1L, validJson, "a1", "l1", now)
    val r2 = GetDirectParentsResult(2L, invalidJson, "a2", "l2", now)

    val dtosEither = GetDirectParentsResult.resultsToParentPartitioningDTOs(Seq(r1, r2))
    assert(dtosEither.isLeft)
  }

}

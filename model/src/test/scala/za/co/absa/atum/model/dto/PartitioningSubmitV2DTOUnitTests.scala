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

package za.co.absa.atum.model.dto

import io.circe.parser._
import io.circe.syntax._
import org.scalatest.funsuite.AnyFunSuiteLike

class PartitioningSubmitV2DTOUnitTests extends AnyFunSuiteLike {

  private val partitioning: PartitioningDTO = Seq(PartitionDTO("key", "value"))

  test("Single-parent compatibility factory wraps the id in a Seq") {
    val withParent = PartitioningSubmitV2DTO(partitioning, Some(1L), "author")
    assert(withParent == PartitioningSubmitV2DTO(partitioning, Seq(1L), "author"))

    val withoutParent = PartitioningSubmitV2DTO(partitioning, None, "author")
    assert(withoutParent == PartitioningSubmitV2DTO(partitioning, Seq.empty, "author"))
  }

  test("Default copyMeasureDefinitions is false when not specified") {
    val dto = PartitioningSubmitV2DTO(partitioning, Seq(1L), "author")
    assert(!dto.copyMeasureDefinitions)
  }

  test("Decoder merges legacy parentPartitioningId with parentPartitioningIds and de-duplicates") {
    val json =
      """{
        |  "partitioning": [{"key": "key", "value": "value"}],
        |  "parentPartitioningId": 1,
        |  "parentPartitioningIds": [1, 2],
        |  "author": "author",
        |  "copyMeasureDefinitions": true
        |}""".stripMargin

    val decoded = decode[PartitioningSubmitV2DTO](json)
    assert(decoded == Right(PartitioningSubmitV2DTO(partitioning, Seq(1L, 2L), "author", copyMeasureDefinitions = true)))
  }

  test("Decoder defaults copyMeasureDefinitions to false and parents to empty when absent") {
    val json =
      """{
        |  "partitioning": [{"key": "key", "value": "value"}],
        |  "author": "author"
        |}""".stripMargin

    val decoded = decode[PartitioningSubmitV2DTO](json)
    assert(decoded == Right(PartitioningSubmitV2DTO(partitioning, Seq.empty, "author", copyMeasureDefinitions = false)))
  }

  test("Encoder round-trips a decoded DTO") {
    val dto = PartitioningSubmitV2DTO(partitioning, Seq(1L, 2L), "author", copyMeasureDefinitions = true)
    val encoded = dto.asJson
    assert(decode[PartitioningSubmitV2DTO](encoded.noSpaces) == Right(dto))
    assert(encoded.hcursor.downField("parentPartitioningIds").as[Seq[Long]] == Right(Seq(1L, 2L)))
  }

}

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

package za.co.absa.atum.server.model

import io.circe.Json
import io.circe.parser._
import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.model.dto.PartitionDTO
import za.co.absa.atum.server.model.database.PartitioningForDB

class PartitioningResultUnitTests extends AnyFunSuiteLike {

  class TestPartitioningResult(id: Long, partitioning: Json, author: String)
    extends PartitioningResult(id, partitioning, author)

  implicit val decoder: io.circe.Decoder[PartitioningForDB] = io.circe.Decoder.instance { cursor =>
    for {
      version <- cursor.downField("version").as[Int]
      keys <- cursor.downField("keys").as[List[String]]
      values <- cursor.downField("keysToValuesMap").as[Map[String, String]]
    } yield PartitioningForDB(version, keys, values)
  }

  test("resultsToPartitioningWithIdDTOs should decode valid PartitioningResults into DTOs") {
    val jsonStr =
      """
        |{
        |  "version": 1,
        |  "keys": ["key1", "key2"],
        |  "keysToValuesMap": {
        |    "key1": "val1",
        |    "key2": "val2"
        |  }
        |}
        |""".stripMargin

    val json = parse(jsonStr).getOrElse(Json.Null)
    val result = new TestPartitioningResult(100L, json, "liam.leibrandt")

    val output = PartitioningResult.resultsToPartitioningWithIdDTOs(Seq(result))

    assert(output.isRight)
    val dtoSeq = output.toOption.get

    assert(dtoSeq.size == 1)
    val dto = dtoSeq.head

    assert(dto.id == 100L)
    assert(dto.author == "liam.leibrandt")
    assert(dto.partitioning.contains(PartitionDTO("key1", "val1")))
    assert(dto.partitioning.contains(PartitionDTO("key2", "val2")))
  }
}


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
import za.co.absa.atum.model.dto.{LineageEdgeDTO, LineageGraphDTO, PartitionDTO, PartitioningWithIdDTO}
import za.co.absa.atum.server.api.database.lineage.functions.GetPartitioningLineageGraph.{GetPartitioningLineageGraphArgs, LineageGraphResult}

import java.time.ZonedDateTime

class GetPartitioningLineageGraphResultUnitTests extends AnyFunSuiteLike {

  private val now = ZonedDateTime.parse("2026-11-12T20:00:00Z")

  private val validGraphJson = parse(
    s"""{
      |  "primaryPartitioningId": 100,
      |  "depth": 3,
      |  "direction": "both",
      |  "nodes": [
      |    {
      |      "id": 100,
      |      "partitioning": {
      |        "version": 1,
      |        "keys": ["key1"],
      |        "keysToValuesMap": {"key1": "val1"}
      |      },
      |      "author": "author100"
      |    },
      |    {
      |      "id": 200,
      |      "partitioning": {
      |        "version": 1,
      |        "keys": ["key2"],
      |        "keysToValuesMap": {"key2": "val2"}
      |      },
      |      "author": "author200"
      |    }
      |  ],
      |  "edges": [
      |    {
      |      "parentPartitioningId": 100,
      |      "childPartitioningId": 200,
      |      "linkedBy": "linker",
      |      "linkedAt": "2026-11-12T20:00:00Z"
      |    }
      |  ]
      |}""".stripMargin
  ).getOrElse(fail("Invalid JSON"))

  private val malformedStructureJson = parse(
    """{
      |  "depth": 3
      |}""".stripMargin
  ).getOrElse(fail("Invalid JSON"))

  private val invalidNodePartitioningJson = parse(
    """{
      |  "primaryPartitioningId": 100,
      |  "depth": 3,
      |  "direction": "both",
      |  "nodes": [
      |    {
      |      "id": 100,
      |      "partitioning": "not-an-object",
      |      "author": "author100"
      |    }
      |  ],
      |  "edges": []
      |}""".stripMargin
  ).getOrElse(fail("Invalid JSON"))

  test("toLineageGraphDTO successfully decodes valid graph JSON") {
    val result = LineageGraphResult(validGraphJson)
    val dtoEither = result.toLineageGraphDTO

    assert(dtoEither.isRight)
    val dto = dtoEither.toOption.get
    assert(dto == LineageGraphDTO(
      primaryPartitioningId = 100L,
      depth = 3,
      direction = "both",
      nodes = Seq(
        PartitioningWithIdDTO(100L, Seq(PartitionDTO("key1", "val1")), "author100"),
        PartitioningWithIdDTO(200L, Seq(PartitionDTO("key2", "val2")), "author200")
      ),
      edges = Seq(
        LineageEdgeDTO(100L, 200L, Some("linker"), Some(now))
      )
    ))
  }

  test("toLineageGraphDTO handles empty nodes and edges arrays") {
    val json = parse(
      """{
        |  "primaryPartitioningId": 100,
        |  "depth": 5,
        |  "direction": "both",
        |  "nodes": [],
        |  "edges": []
        |}""".stripMargin
    ).getOrElse(fail("Invalid JSON"))

    val dtoEither = LineageGraphResult(json).toLineageGraphDTO
    assert(dtoEither == Right(LineageGraphDTO(100L, 5, "both", Seq.empty, Seq.empty)))
  }

  test("toLineageGraphDTO defaults SQL null nodes and edges to Seq.empty") {
    val json = parse(
      """{
        |  "primaryPartitioningId": 100,
        |  "depth": 5,
        |  "direction": "both",
        |  "nodes": null,
        |  "edges": null
        |}""".stripMargin
    ).getOrElse(fail("Invalid JSON"))

    val dtoEither = LineageGraphResult(json).toLineageGraphDTO
    assert(dtoEither == Right(LineageGraphDTO(100L, 5, "both", Seq.empty, Seq.empty)))
  }

  test("toLineageGraphDTO decodes edges with omitted optional linkedBy and linkedAt") {
    val json = parse(
      """{
        |  "primaryPartitioningId": 100,
        |  "depth": 3,
        |  "direction": "downstream",
        |  "nodes": [],
        |  "edges": [
        |    {
        |      "parentPartitioningId": 100,
        |      "childPartitioningId": 200
        |    }
        |  ]
        |}""".stripMargin
    ).getOrElse(fail("Invalid JSON"))

    val dtoEither = LineageGraphResult(json).toLineageGraphDTO
    assert(
      dtoEither == Right(
        LineageGraphDTO(100L, 3, "downstream", Seq.empty, Seq(LineageEdgeDTO(100L, 200L, None, None)))
      )
    )
  }

  test("toLineageGraphDTO maps a missing key in keysToValuesMap to empty string") {
    val json = parse(
      """{
        |  "primaryPartitioningId": 100,
        |  "depth": 3,
        |  "direction": "both",
        |  "nodes": [
        |    {
        |      "id": 100,
        |      "partitioning": {
        |        "version": 1,
        |        "keys": ["key1", "missingKey"],
        |        "keysToValuesMap": {"key1": "val1"}
        |      },
        |      "author": "author100"
        |    }
        |  ],
        |  "edges": []
        |}""".stripMargin
    ).getOrElse(fail("Invalid JSON"))

    val dtoEither = LineageGraphResult(json).toLineageGraphDTO
    assert(
      dtoEither == Right(
        LineageGraphDTO(
          100L,
          3,
          "both",
          Seq(PartitioningWithIdDTO(100L, Seq(PartitionDTO("key1", "val1"), PartitionDTO("missingKey", "")), "author100")),
          Seq.empty
        )
      )
    )
  }

  test("toLineageGraphDTO fails on invalid edges JSON") {
    val json = parse(
      """{
        |  "primaryPartitioningId": 100,
        |  "depth": 3,
        |  "direction": "both",
        |  "nodes": [],
        |  "edges": [
        |    {
        |      "parentPartitioningId": "not-a-long",
        |      "childPartitioningId": 200
        |    }
        |  ]
        |}""".stripMargin
    ).getOrElse(fail("Invalid JSON"))

    assert(LineageGraphResult(json).toLineageGraphDTO.isLeft)
  }

  test("toLineageGraphDTO fails on malformed top-level graph structure") {
    val result = LineageGraphResult(malformedStructureJson)
    val dtoEither = result.toLineageGraphDTO

    assert(dtoEither.isLeft)
  }

  test("toLineageGraphDTO fails on invalid node partitioning JSON") {
    val result = LineageGraphResult(invalidNodePartitioningJson)
    val dtoEither = result.toLineageGraphDTO

    assert(dtoEither.isLeft)
  }

  test("toLineageGraphDTO decodes truncated and expandable boundary node IDs when present") {
    val json = parse(
      """{
        |  "primaryPartitioningId": 200,
        |  "depth": 1,
        |  "direction": "both",
        |  "nodes": [],
        |  "edges": [],
        |  "truncated": true,
        |  "expandableUpstreamNodeIds": [100],
        |  "expandableDownstreamNodeIds": [300, 301]
        |}""".stripMargin
    ).getOrElse(fail("Invalid JSON"))

    val dtoEither = LineageGraphResult(json).toLineageGraphDTO
    assert(
      dtoEither == Right(
        LineageGraphDTO(
          primaryPartitioningId = 200L,
          depth = 1,
          direction = "both",
          nodes = Seq.empty,
          edges = Seq.empty,
          truncated = true,
          expandableUpstreamNodeIds = Seq(100L),
          expandableDownstreamNodeIds = Seq(300L, 301L)
        )
      )
    )
  }

  test("GetPartitioningLineageGraphArgs fields") {
    val args = GetPartitioningLineageGraphArgs(123L, "upstream", 2)
    assert(args.partitioningId == 123L)
    assert(args.direction == "upstream")
    assert(args.depth == 2)
  }

}

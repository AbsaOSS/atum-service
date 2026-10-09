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

package za.co.absa.atum.reader

import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.capabilities
import sttp.client3.monad.IdMonad
import sttp.client3.{Identity, Response, SttpBackend}
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode
import sttp.model.Uri.QuerySegment.KeyValue
import sttp.monad.MonadError
import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.model.dto.{CheckpointWithPartitioningDTO, MeasureDTO, MeasureResultDTO, MeasurementDTO, PartitioningWithIdDTO}
import za.co.absa.atum.model.envelopes.Pagination
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.model.types.basic.{AtumPartitions, AtumPartitionsOps}
import za.co.absa.atum.reader.FlowReaderUnitTests._
import za.co.absa.atum.reader.PartitioningReaderUnitTests.checkpointsResponseWithProperties
import za.co.absa.atum.reader.requests.CheckpointFilter
import za.co.absa.atum.reader.server.ServerConfig

import java.time.ZonedDateTime
import java.util.UUID
import scala.annotation.nowarn

class FlowReaderUnitTests extends AnyFunSuiteLike {
  private implicit val serverConfig: ServerConfig = ServerConfig.fromConfig()
  private implicit val monadError: MonadError[Identity] = IdMonad

  test("mainFlowPartitioning is the same as partitioning") {
    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    implicit val server: SttpBackend[Identity, Any] = SttpBackendStub.synchronous

    val result = FlowReader(atumPartitions).mainFlowPartitioning
    assert(result == atumPartitions)
  }

  test("The flow checkpoints are properly queried and delivered as DTO") {
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List("partitionings")) =>
          assert(r.uri.querySegments.contains(KeyValue("partitioning", partitioningEncoded)))
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List("partitionings", "7", "main-flow")) =>
          Response.ok(flowResponse)
        case r if r.uri.path.endsWith(List("checkpoints")) =>
          assert(r.uri.querySegments.contains(KeyValue("offset", "0")))
          assert(r.uri.querySegments.contains(KeyValue("limit", "10")))
          Response.ok(checkpointsResponse)
      }

    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    val expectedData: PaginatedResponse[CheckpointWithPartitioningDTO] = PaginatedResponse(
      data = Seq(
        CheckpointWithPartitioningDTO(
          id = UUID.fromString("51ee4257-0842-4d28-8779-8ecb19ae7bf0"),
          name = "Test checkpoints 1",
          author = "Jason Bourne",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.parse("2024-12-30T16:01:36.5042011+01:00[Europe/Budapest]"),
          processEndTime = Some(ZonedDateTime.parse("2024-12-30T16:01:36.5052109+01:00[Europe/Budapest]")),
          measurements = Set(
            MeasurementDTO(
              measure = MeasureDTO(
                measureName = "Fictional",
                measuredColumns = Seq("x", "y", "z")
              ),
              result = MeasureResultDTO(
                mainValue = TypedValue("1", ResultValueType.LongValue),
              )
            )
          ),
          partitioning = PartitioningWithIdDTO(
            id = 7,
            atumPartitions.toPartitioningDTO,
            author = "James Bond"
          )
        ),
        CheckpointWithPartitioningDTO(
          id = UUID.fromString("8b7f603e-3fc3-474f-aced-a7af054589a2"),
          name = "Test checkpoints 2",
          author = "John McClane",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.parse("2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]"),
          processEndTime = None,
          measurements = Set(),
          partitioning = PartitioningWithIdDTO(
            id = 7,
            atumPartitions.toPartitioningDTO,
            author = "James Bond"
          )
      )
      ),
      pagination = Pagination(
        limit = 10,
        offset = 0,
        hasMore = false
      ),
      requestId = UUID.fromString("29ce91a7-b668-41d2-a160-26402551fb0b")
    )

    val reader = FlowReader(atumPartitions)
    val result = reader.getCheckpointsPage()
    assert(result == Right(expectedData))
  }

  test("The flow checkpoints are properly queried with name and delivered as DTO") {
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List("partitionings")) =>
          assert(r.uri.querySegments.contains(KeyValue("partitioning", partitioningEncoded)))
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List("partitionings", "7", "main-flow")) =>
          Response.ok(flowResponse)
        case r if r.uri.path.endsWith(List("checkpoints")) =>
          assert(r.uri.querySegments.contains(KeyValue("offset", "0")))
          assert(r.uri.querySegments.contains(KeyValue("limit", "10")))
          assert(r.uri.querySegments.contains(KeyValue("checkpoint-name", "Test checkpoints 1")))
          Response.ok(checkpointsResponse)
      }

    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    val expectedData: PaginatedResponse[CheckpointWithPartitioningDTO] = PaginatedResponse(
      data = Seq(
        CheckpointWithPartitioningDTO(
          id = UUID.fromString("51ee4257-0842-4d28-8779-8ecb19ae7bf0"),
          name = "Test checkpoints 1",
          author = "Jason Bourne",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.parse("2024-12-30T16:01:36.5042011+01:00[Europe/Budapest]"),
          processEndTime = Some(ZonedDateTime.parse("2024-12-30T16:01:36.5052109+01:00[Europe/Budapest]")),
          measurements = Set(
            MeasurementDTO(
              measure = MeasureDTO(
                measureName = "Fictional",
                measuredColumns = Seq("x", "y", "z")
              ),
              result = MeasureResultDTO(
                mainValue = TypedValue("1", ResultValueType.LongValue),
              )
            )
          ),
          partitioning = PartitioningWithIdDTO(
            id = 7,
            atumPartitions.toPartitioningDTO,
            author = "James Bond"
          )
        ),
        CheckpointWithPartitioningDTO(
          id = UUID.fromString("8b7f603e-3fc3-474f-aced-a7af054589a2"),
          name = "Test checkpoints 2",
          author = "John McClane",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.parse("2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]"),
          processEndTime = None,
          measurements = Set(),
          partitioning = PartitioningWithIdDTO(
            id = 7,
            atumPartitions.toPartitioningDTO,
            author = "James Bond"
          )
        )
      ),
      pagination = Pagination(
        limit = 10,
        offset = 0,
        hasMore = false
      ),
      requestId = UUID.fromString("29ce91a7-b668-41d2-a160-26402551fb0b")
    )

    val reader = FlowReader(atumPartitions)
    val result = (reader.getCheckpointsOfNamePage("Test checkpoints 1"): @nowarn("cat=deprecation"))
    assert(result == Right(expectedData))
  }

  test("The flow checkpoints are properly queried with name and delivered as DTO, including properties") {
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List("partitionings")) =>
          assert(r.uri.querySegments.contains(KeyValue("partitioning", partitioningEncoded)))
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List("partitionings", "7", "main-flow")) =>
          Response.ok(flowResponse)
        case r if r.uri.path.endsWith(List("checkpoints")) =>
          assert(r.uri.querySegments.contains(KeyValue("offset", "0")))
          assert(r.uri.querySegments.contains(KeyValue("limit", "10")))
          assert(r.uri.querySegments.contains(KeyValue("checkpoint-name", "Test checkpoints 1")))
          assert(r.uri.querySegments.contains(KeyValue("include-properties", "true")))
          Response.ok(checkpointsResponseWithProperties)
      }

    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    val expectedData: PaginatedResponse[CheckpointWithPartitioningDTO] = PaginatedResponse(
      data = Seq(
        CheckpointWithPartitioningDTO(
          id = UUID.fromString("51ee4257-0842-4d28-8779-8ecb19ae7bf0"),
          name = "Test checkpoints 1",
          author = "Jason Bourne",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.parse("2024-12-30T16:01:36.5042011+01:00[Europe/Budapest]"),
          processEndTime = Some(ZonedDateTime.parse("2024-12-30T16:01:36.5052109+01:00[Europe/Budapest]")),
          measurements = Set(
            MeasurementDTO(
              measure = MeasureDTO(
                measureName = "Fictional",
                measuredColumns = Seq("x", "y", "z")
              ),
              result = MeasureResultDTO(
                mainValue = TypedValue("1", ResultValueType.LongValue),
              )
            )
          ),
          partitioning = PartitioningWithIdDTO(
            id = 7,
            atumPartitions.toPartitioningDTO,
            author = "James Bond"
          ),
          properties = Some(Map(
            "prop1" -> "value1",
            "prop2" -> "value2"
          ))
        ),
        CheckpointWithPartitioningDTO(
          id = UUID.fromString("8b7f603e-3fc3-474f-aced-a7af054589a2"),
          name = "Test checkpoints 2",
          author = "John McClane",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.parse("2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]"),
          processEndTime = None,
          measurements = Set(),
          partitioning = PartitioningWithIdDTO(
            id = 7,
            atumPartitions.toPartitioningDTO,
            author = "James Bond"
          ),
          properties = Some(Map(
            "prop1" -> "value3"
          ))
        )
      ),
      pagination = Pagination(
        limit = 10,
        offset = 0,
        hasMore = false
      ),
      requestId = UUID.fromString("29ce91a7-b668-41d2-a160-26402551fb0b")
    )

    val reader = FlowReader(atumPartitions)
    val result = (reader.getCheckpointsOfNamePage("Test checkpoints 1", includeProperties = true): @nowarn("cat=deprecation"))
    assert(result == Right(expectedData))
  }

  test("The flow checkpoints are properly queried by checkpoint properties and delivered as DTO") {
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List("partitionings")) =>
          assert(r.uri.querySegments.contains(KeyValue("partitioning", partitioningEncoded)))
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List("partitionings", "7", "main-flow")) =>
          Response.ok(flowResponse)
        case r if r.uri.path.endsWith(List("checkpoints")) =>
          assert(r.uri.querySegments.contains(KeyValue("offset", "0")))
          assert(r.uri.querySegments.contains(KeyValue("limit", "10")))
          assert(r.uri.querySegments.contains(KeyValue("checkpoint-properties", "eyJleGVjdXRpb25JRCI6IjAxOWY4OTgxLTc4NjgtNzlmYy04MWQzLTgxNDNhNDcwNmY4YSJ9")))
          Response.ok(checkpointsResponse)
      }

    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))

    val reader = FlowReader(atumPartitions)
    // base64url-encoded {"executionID":"019f8981-7868-79fc-81d3-8143a4706f8a"}
    val result = (
      reader.getCheckpointsByPropertiesPage(Map("executionID" -> "019f8981-7868-79fc-81d3-8143a4706f8a")): @nowarn("cat=deprecation")
    )
    assert(result.isRight)
  }

  test("The flow checkpoints are queried with name, multi-value properties and time window in a single request") {
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List("partitionings")) =>
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List("partitionings", "7", "main-flow")) =>
          Response.ok(flowResponse)
        case r if r.uri.path.endsWith(List("flows", "42", "checkpoints")) =>
          assert(r.uri.querySegments.contains(KeyValue("limit", "5")))
          assert(r.uri.querySegments.contains(KeyValue("offset", "10")))
          assert(r.uri.querySegments.contains(KeyValue("include-properties", "true")))
          assert(r.uri.querySegments.contains(KeyValue("checkpoint-name", "Test checkpoints 1")))
          assert(r.uri.querySegments.contains(KeyValue("checkpoint-properties", multiValuePropertiesEncoded)))
          assert(r.uri.querySegments.contains(KeyValue("from", "2026-06-01T00:00:00Z")))
          assert(r.uri.querySegments.contains(KeyValue("to", "2026-08-01T00:00:00Z")))
          assert(r.uri.querySegments.contains(KeyValue("latest-first", "false")))
          Response.ok(checkpointsResponse)
      }

    val filter = CheckpointFilter(
      name = Some("Test checkpoints 1"),
      properties = Map("executionID" -> Set("id2", "id1")),
      from = Some(ZonedDateTime.parse("2026-06-01T02:00:00+02:00")),
      to = Some(ZonedDateTime.parse("2026-08-01T00:00:00Z")),
      latestFirst = Some(false)
    )
    val result = FlowReader(AtumPartitions(List("a" -> "b", "c" -> "d")))
      .getCheckpointsPage(pageSize = 5, offset = 10, includeProperties = true, filter = filter)
    assert(result.map(_.data.size) == Right(2))
  }

  test("All flow checkpoints satisfying the filter are queried page by page") {
    var flowIdQueries = 0
    var queriedOffsets = Vector.empty[String]
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List("partitionings")) =>
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List("partitionings", "7", "main-flow")) =>
          flowIdQueries += 1
          Response.ok(flowResponse)
        case r if r.uri.path.endsWith(List("flows", "42", "checkpoints")) =>
          assert(r.uri.querySegments.contains(KeyValue("limit", "2")))
          assert(r.uri.querySegments.contains(KeyValue("from", "2026-06-01T00:00:00Z")))
          val offset = r.uri.paramsMap("offset")
          queriedOffsets :+= offset
          if (offset == "0") Response.ok(checkpointsResponseHasMore) else Response.ok(checkpointsResponse)
      }

    val filter = CheckpointFilter(from = Some(ZonedDateTime.parse("2026-06-01T00:00:00Z")))
    val result = FlowReader(AtumPartitions(List("a" -> "b", "c" -> "d"))).getAllCheckpoints(filter, pageSize = 2)

    assert(result.map(_.size) == Right(4))
    assert(queriedOffsets == Vector("0", "2"))
    assert(flowIdQueries == 1)
  }

  test("Querying all flow checkpoints stops at the first failing page") {
    var queriedOffsets = Vector.empty[String]
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List("partitionings")) =>
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List("partitionings", "7", "main-flow")) =>
          Response.ok(flowResponse)
        case r if r.uri.path.endsWith(List("flows", "42", "checkpoints")) =>
          val offset = r.uri.paramsMap("offset")
          queriedOffsets :+= offset
          if (offset == "0") Response.ok(checkpointsResponseHasMore)
          else Response(internalServerErrorResponse, StatusCode.InternalServerError)
      }

    val result = FlowReader(AtumPartitions(List("a" -> "b", "c" -> "d"))).getAllCheckpoints(pageSize = 2)

    assert(result.isLeft)
    assert(queriedOffsets == Vector("0", "2"))
  }

  test("Instantiate FlowReader with implicit arguments passed explicitly") {
    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    implicit val server: SttpBackend[Identity, Any] = SttpBackendStub.synchronous

    val flowReader = FlowReader(atumPartitions)(serverConfig, server, monadError)
    assert(flowReader.isInstanceOf[FlowReader[Identity]])
  }

  test("The flow partitioning author is properly queried and delivered") {
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List("partitionings")) =>
          assert(r.uri.querySegments.contains(KeyValue("partitioning", partitioningEncoded)))
          Response.ok(partitioningResponse)
      }

    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))

    val reader = FlowReader(atumPartitions)
    val result = reader.getAuthor
    assert(result == Right("James Bond"))
  }

}

object FlowReaderUnitTests {

  // base64url-encoded {"executionID":["id1","id2"]}
  private val multiValuePropertiesEncoded = "eyJleGVjdXRpb25JRCI6WyJpZDEiLCJpZDIiXX0="

  private val internalServerErrorResponse =
    """{"message": "boom", "requestId": "29ce91a7-b668-41d2-a160-26402551fb0b"}"""

  private val partitioningEncoded = "W3sia2V5IjoiYSIsInZhbHVlIjoiYiJ9LHsia2V5IjoiYyIsInZhbHVlIjoiZCJ9XQ=="

  private val partitioningResponse =
    """
      |{
      |  "data" : {
      |    "id" : 7,
      |    "partitioning" : [
      |      {
      |        "key" : "a",
      |        "value" : "b"
      |      },
      |      {
      |        "key" : "c",
      |        "value" : "d"
      |      }
      |    ],
      |    "author" : "James Bond"
      |  },
      |  "requestId" : "a8463570-b61f-4c35-9362-4d550848767e"
      |}
      |""".stripMargin


  private val flowResponse =
    """
      |{
      |  "data" : {
      |    "id" : 42,
      |    "name" : "Test flow",
      |    "description" : "This is a test flow",
      |    "fromPattern" : false
      |  },
      |  "requestId" : "c1343c53-463e-4ac0-80f8-c597c2f1f895"
      |}
      |""".stripMargin

  private val checkpointsResponse =
    """
      |{
      |  "data" : [
      |    {
      |      "id" : "51ee4257-0842-4d28-8779-8ecb19ae7bf0",
      |      "name" : "Test checkpoints 1",
      |      "author" : "Jason Bourne",
      |      "measuredByAtumAgent" : true,
      |      "processStartTime" : "2024-12-30T16:01:36.5042011+01:00[Europe/Budapest]",
      |      "processEndTime" : "2024-12-30T16:01:36.5052109+01:00[Europe/Budapest]",
      |      "measurements" : [
      |        {
      |          "measure" : {
      |            "measureName" : "Fictional",
      |            "measuredColumns" : [
      |              "x",
      |              "y",
      |              "z"
      |            ]
      |          },
      |          "result" : {
      |            "mainValue" : {
      |              "value" : "1",
      |              "valueType" : "Long"
      |            },
      |            "supportValues" : {
      |
      |            }
      |          }
      |        }
      |      ],
      |      "partitioning" : {
      |        "id" : 7,
      |        "partitioning" : [
      |          {
      |            "key" : "a",
      |            "value" : "b"
      |          },
      |          {
      |            "key" : "c",
      |            "value" : "d"
      |          }
      |        ],
      |        "author" : "James Bond"
      |      }
      |    },
      |    {
      |      "id" : "8b7f603e-3fc3-474f-aced-a7af054589a2",
      |      "name" : "Test checkpoints 2",
      |      "author" : "John McClane",
      |      "measuredByAtumAgent" : true,
      |      "processStartTime" : "2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]",
      |      "measurements" : [
      |      ],
      |      "partitioning" : {
      |        "id" : 7,
      |        "partitioning" : [
      |          {
      |            "key" : "a",
      |            "value" : "b"
      |          },
      |          {
      |            "key" : "c",
      |            "value" : "d"
      |          }
      |        ],
      |        "author" : "James Bond"
      |      }
      |    }
      |  ],
      |  "pagination" : {
      |    "limit" : 10,
      |    "offset" : 0,
      |    "hasMore" : false
      |  },
      |  "requestId" : "29ce91a7-b668-41d2-a160-26402551fb0b"
      |}
      |""".stripMargin

  private val checkpointsResponseHasMore = checkpointsResponse.replace("\"hasMore\" : false", "\"hasMore\" : true")

  private val checkpointsResponseWithProperties =
    """
      |{
      |  "data" : [
      |    {
      |      "id" : "51ee4257-0842-4d28-8779-8ecb19ae7bf0",
      |      "name" : "Test checkpoints 1",
      |      "author" : "Jason Bourne",
      |      "measuredByAtumAgent" : true,
      |      "processStartTime" : "2024-12-30T16:01:36.5042011+01:00[Europe/Budapest]",
      |      "processEndTime" : "2024-12-30T16:01:36.5052109+01:00[Europe/Budapest]",
      |      "measurements" : [
      |        {
      |          "measure" : {
      |            "measureName" : "Fictional",
      |            "measuredColumns" : [
      |              "x",
      |              "y",
      |              "z"
      |            ]
      |          },
      |          "result" : {
      |            "mainValue" : {
      |              "value" : "1",
      |              "valueType" : "Long"
      |            },
      |            "supportValues" : { }
      |          }
      |        }
      |      ],
      |      "properties": {
      |        "prop1": "value1",
      |        "prop2": "value2"
      |      },
      |      "partitioning" : {
      |        "id" : 7,
      |        "partitioning" : [
      |          {
      |            "key" : "a",
      |            "value" : "b"
      |          },
      |          {
      |            "key" : "c",
      |            "value" : "d"
      |          }
      |        ],
      |        "author" : "James Bond"
      |      }
      |    },
      |    {
      |      "id" : "8b7f603e-3fc3-474f-aced-a7af054589a2",
      |      "name" : "Test checkpoints 2",
      |      "author" : "John McClane",
      |      "measuredByAtumAgent" : true,
      |      "processStartTime" : "2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]",
      |      "processEndTime" : null,
      |      "measurements" : [ ],
      |      "properties": {
      |        "prop1": "value3"
      |      },
      |      "partitioning" : {
      |        "id" : 7,
      |        "partitioning" : [
      |          {
      |            "key" : "a",
      |            "value" : "b"
      |          },
      |          {
      |            "key" : "c",
      |            "value" : "d"
      |          }
      |        ],
      |        "author" : "James Bond"
      |      }
      |    }
      |  ],
      |  "pagination" : {
      |    "limit" : 10,
      |    "offset" : 0,
      |    "hasMore" : false
      |  },
      |  "requestId" : "29ce91a7-b668-41d2-a160-26402551fb0b"
      |}
      |""".stripMargin

}

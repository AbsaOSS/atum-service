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

import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.capabilities
import sttp.client3.monad.IdMonad
import sttp.client3.{Identity, Response, SttpBackend}
import sttp.client3.testing.SttpBackendStub
import sttp.model.Uri.QuerySegment.KeyValue
import sttp.monad.MonadError
import za.co.absa.atum.model.ApiPaths.V2Paths
import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataItemDTO, CheckpointV2DTO, MeasureDTO, MeasureResultDTO, MeasurementDTO}
import za.co.absa.atum.model.envelopes.Pagination
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.model.types.basic.AtumPartitions
import za.co.absa.atum.reader.PartitioningReaderUnitTests._
import za.co.absa.atum.reader.server.ServerConfig

import java.time.ZonedDateTime
import java.util.UUID


class PartitioningReaderUnitTests extends AnyFunSuiteLike {
  private implicit val serverConfig: ServerConfig = ServerConfig.fromConfig()
  private implicit val monad: MonadError[Identity] = IdMonad

  test("The partitioning checkpoints are properly queried and delivered as DTO") {
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List(V2Paths.Partitionings)) =>
          assert(r.uri.querySegments.contains(KeyValue("partitioning", partitioningEncoded)))
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List(V2Paths.Partitionings, "7", V2Paths.Checkpoints)) =>
          assert(r.uri.querySegments.contains(KeyValue("offset", "0")))
          assert(r.uri.querySegments.contains(KeyValue("limit", "10")))
          Response.ok(checkpointsResponse)
      }

    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    val expected: PaginatedResponse[CheckpointV2DTO] = PaginatedResponse(
      data = Seq(
        CheckpointV2DTO(
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
          )
        ),
        CheckpointV2DTO(
          id = UUID.fromString("8b7f603e-3fc3-474f-aced-a7af054589a2"),
          name = "Test checkpoints 2",
          author = "John McClane",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.parse("2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]"),
          processEndTime = None,
          measurements = Set()
        )
      ),
      pagination = Pagination(
        limit = 10,
        offset = 0,
        hasMore = false
      ),
      requestId = UUID.fromString("29ce91a7-b668-41d2-a160-26402551fb0b")
    )

    val reader = PartitioningReader(atumPartitions)
    val result = reader.getCheckpointsPage()
    assert(result == Right(expected))
  }

  test("The partitioning checkpoints are properly queried and delivered as DTO, including properties") {
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List(V2Paths.Partitionings)) =>
          assert(r.uri.querySegments.contains(KeyValue("partitioning", partitioningEncoded)))
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List(V2Paths.Partitionings, "7", V2Paths.Checkpoints)) =>
          assert(r.uri.querySegments.contains(KeyValue("offset", "0")))
          assert(r.uri.querySegments.contains(KeyValue("limit", "10")))
          assert(r.uri.querySegments.contains(KeyValue("include-properties", "true")))
          Response.ok(checkpointsResponseWithProperties)
      }

    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    val expected: PaginatedResponse[CheckpointV2DTO] = PaginatedResponse(
      data = Seq(
        CheckpointV2DTO(
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
          properties = Some(Map(
            "prop1" -> "value1",
            "prop2" -> "value2"
          ))
        ),
        CheckpointV2DTO(
          id = UUID.fromString("8b7f603e-3fc3-474f-aced-a7af054589a2"),
          name = "Test checkpoints 2",
          author = "John McClane",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.parse("2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]"),
          processEndTime = None,
          measurements = Set(),
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

    val reader = PartitioningReader(atumPartitions)
    val result = reader.getCheckpointsPage(includeProperties = true)
    assert(result == Right(expected))
  }

  test("The partitioning checkpoints are properly queried including name and delivered as DTO") {
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List(V2Paths.Partitionings)) =>
          assert(r.uri.querySegments.contains(KeyValue("partitioning", partitioningEncoded)))
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List(V2Paths.Partitionings, "7", V2Paths.Checkpoints)) =>
          assert(r.uri.querySegments.contains(KeyValue("offset", "0")))
          assert(r.uri.querySegments.contains(KeyValue("limit", "10")))
          assert(r.uri.querySegments.contains(KeyValue("checkpoint-name", "Test checkpoints 1")))
          Response.ok(checkpointsResponse)
      }

    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    val expected: PaginatedResponse[CheckpointV2DTO] = PaginatedResponse(
      data = Seq(
        CheckpointV2DTO(
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
          )
        ),
        CheckpointV2DTO(
          id = UUID.fromString("8b7f603e-3fc3-474f-aced-a7af054589a2"),
          name = "Test checkpoints 2",
          author = "John McClane",
          measuredByAtumAgent = true,
          processStartTime = ZonedDateTime.parse("2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]"),
          processEndTime = None,
          measurements = Set()
        )
      ),
      pagination = Pagination(
        limit = 10,
        offset = 0,
        hasMore = false
      ),
      requestId = UUID.fromString("29ce91a7-b668-41d2-a160-26402551fb0b")
    )

    val reader = PartitioningReader(atumPartitions)
    val result = reader.getCheckpointsOfNamePage("Test checkpoints 1")
    assert(result == Right(expected))
  }

  test("The partitioning additional data are properly queried and delivered as DTO") {
    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    implicit val server: SttpBackendStub[Identity, capabilities.WebSockets] = SttpBackendStub.synchronous
      .whenRequestMatchesPartial {
        case r if r.uri.path.endsWith(List("partitionings")) =>
          assert(r.uri.querySegments.contains(KeyValue("partitioning", partitioningEncoded)))
          Response.ok(partitioningResponse)
        case r if r.uri.path.endsWith(List("partitionings", "7", "additional-data")) =>
          Response.ok(additionalDataResponse)
      }

    val expected = AdditionalDataDTO(data =
      Map(
        "key1" -> Some(AdditionalDataItemDTO("value1", "Luke Skywalker")),
        "key2" -> None,
        "key3" -> Some(AdditionalDataItemDTO("value3", "Han Solo"))
      )
    )
    val reader = PartitioningReader[Identity](atumPartitions)
    val result = reader.getAdditionalData
    assert(result == Right(expected))
  }
}

object PartitioningReaderUnitTests {
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
      |      ]
      |    },
      |    {
      |      "id" : "8b7f603e-3fc3-474f-aced-a7af054589a2",
      |      "name" : "Test checkpoints 2",
      |      "author" : "John McClane",
      |      "measuredByAtumAgent" : true,
      |      "processStartTime" : "2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]",
      |      "processEndTime" : null,
      |      "measurements" : [
      |      ]
      |    }
      |  ],
      |  "pagination" : {
      |    "limit" : 10,
      |    "offset" : 0,
      |    "hasMore" : false
      |  },
      |  "requestId" : "29ce91a7-b668-41d2-a160-26402551fb0b"
      |}
      |
      |""".stripMargin

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
      |            "supportValues" : {
      |            }
      |          }
      |        }
      |      ],
      |      "properties": {
      |        "prop1": "value1",
      |        "prop2": "value2"
      |      }
      |    },
      |    {
      |      "id" : "8b7f603e-3fc3-474f-aced-a7af054589a2",
      |      "name" : "Test checkpoints 2",
      |      "author" : "John McClane",
      |      "measuredByAtumAgent" : true,
      |      "processStartTime" : "2024-12-30T16:02:36.5042011+01:00[Europe/Budapest]",
      |      "processEndTime" : null,
      |      "measurements" : [
      |      ],
      |      "properties": {
      |        "prop1": "value3"
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

  private val additionalDataResponse =
    """
      |{
      |  "data" : {
      |    "data" : {
      |      "key1" : {
      |        "value" : "value1",
      |        "author" : "Luke Skywalker"
      |      },
      |      "key2" : null,
      |      "key3" : {
      |        "value" : "value3",
      |        "author" : "Han Solo"
      |      }
      |    }
      |  },
      |  "requestId" : "0fec02e3-9a69-4a90-a14c-5ad9666d7dd7"
      |}
      |""".stripMargin

}

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
import sttp.model.Uri.QuerySegment.KeyValue
import sttp.monad.MonadError
import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.model.dto.{CheckpointWithPartitioningDTO, MeasureDTO, MeasureResultDTO, MeasurementDTO, PartitioningWithIdDTO}
import za.co.absa.atum.model.envelopes.Pagination
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.model.types.basic.{AtumPartitions, AtumPartitionsOps}
import za.co.absa.atum.reader.FlowReaderUnitTests._
import za.co.absa.atum.reader.server.ServerConfig

import java.time.ZonedDateTime
import java.util.UUID

class FlowReaderUnitTests extends AnyFunSuiteLike {
  private implicit val serverConfig: ServerConfig = ServerConfig.fromConfig()
  private implicit val monad: MonadError[Identity] = IdMonad

  test("mainFlowPartitioning is the same as partitioning") {
    val atumPartitions: AtumPartitions = AtumPartitions(List(
      "a" -> "b",
      "c" -> "d"
    ))
    implicit val server: SttpBackend[Identity, Any] = SttpBackendStub.synchronous

    val result = new FlowReader(atumPartitions).mainFlowPartitioning
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

    val reader = new FlowReader(atumPartitions)
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

    val reader = new FlowReader(atumPartitions)
    val result = reader.getCheckpointsOfNamePage("Test checkpoints 1")
    assert(result == Right(expectedData))
  }

}

object FlowReaderUnitTests {

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
}

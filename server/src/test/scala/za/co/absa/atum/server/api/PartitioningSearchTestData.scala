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

package za.co.absa.atum.server.api

import io.circe.Json
import io.circe.syntax._
import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningWithIdDTO}
import za.co.absa.atum.server.model.database.PartitioningForDB

import java.nio.charset.StandardCharsets.UTF_8
import java.time.ZonedDateTime
import java.util.{Base64, UUID}

trait PartitioningSearchTestData {
  protected val searchKeys: Map[String, String] =
    Map("source" -> "\u96ea/\u6771\u4eac +&= \ud83d\ude42", "empty" -> "")
  protected val searchFrom: ZonedDateTime = ZonedDateTime.parse("2026-06-01T08:00:00+05:30")
  protected val searchTo: ZonedDateTime = ZonedDateTime.parse("2026-06-01T07:00:00-04:00")
  protected val searchOffset: Long = 4294967297L
  protected val searchRequestId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000123")
  protected val searchPartitionings: Seq[PartitioningWithIdDTO] = Seq(
    PartitioningWithIdDTO(
      11L,
      Seq(PartitionDTO("source", searchKeys("source")), PartitionDTO("empty", ""), PartitionDTO("date", "2026-06-01")),
      "Search author"
    ),
    PartitioningWithIdDTO(
      22L,
      Seq(PartitionDTO("date", "2026-06-02"), PartitionDTO("empty", ""), PartitionDTO("source", searchKeys("source"))),
      "Other author"
    )
  )

  protected def searchPartitioningJson(index: Int): Json =
    PartitioningForDB.fromSeqPartitionDTO(searchPartitionings(index).partitioning).asJson

  protected def encodeSearchJson(json: String): String =
    Base64.getUrlEncoder.withoutPadding().encodeToString(json.getBytes(UTF_8))
}

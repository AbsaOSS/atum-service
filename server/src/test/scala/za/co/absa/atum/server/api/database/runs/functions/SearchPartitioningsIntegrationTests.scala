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

package za.co.absa.atum.server.api.database.runs.functions

import io.circe.parser.decode
import io.circe.syntax._
import org.mockito.Mockito.mock
import sttp.client3.UriContext
import sttp.model.StatusCode
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.envelopes.Pagination
import za.co.absa.atum.model.envelopes.SuccessResponse.PaginatedResponse
import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.{PartitioningSearchTestData, TestTransactorProvider}
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings
import za.co.absa.atum.server.api.database.runs.functions.SearchPartitionings.{SearchPartitioningsArgs, SearchPartitioningsResult}
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpointV2.WriteCheckpointArgs
import za.co.absa.atum.server.api.v2.controller.PartitioningControllerImpl
import za.co.absa.atum.server.api.v2.http.PartitioningSearchTestSupport
import za.co.absa.atum.server.api.v2.repository.PartitioningRepositoryImpl
import za.co.absa.atum.server.api.v2.service.PartitioningServiceImpl
import za.co.absa.atum.server.model.PartitioningResult
import za.co.absa.atum.server.model.database.PartitioningForDB
import za.co.absa.db.fadb.status.{FunctionStatus, Row}
import zio.interop.catz.asyncInstance
import zio.test._
import zio.{Scope, ZIO}

import java.nio.charset.StandardCharsets.UTF_8
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

object SearchPartitioningsIntegrationTests extends ConfigProviderTest with PartitioningSearchTestData {
  private val source = "search-integration-\u96ea/\u6771\u4eac +&= \ud83d\ude42"
  private val keys = Map("source" -> source, "kind" -> "main", "empty" -> "")
  private val from = ZonedDateTime.parse("2216-06-01T08:00:00+05:30")
  private val to = ZonedDateTime.parse("2216-06-01T00:30:00-04:00")
  private val baseArgs = SearchPartitioningsArgs(10, 0L, Some(keys), None, None)

  private case class Fixture(
    withoutCheckpoint: PartitioningWithIdDTO,
    historical: PartitioningWithIdDTO,
    atFrom: PartitioningWithIdDTO,
    atTo: PartitioningWithIdDTO,
    otherKind: PartitioningWithIdDTO,
    beforeFrom: PartitioningWithIdDTO
  ) {
    val keyMatches: Seq[PartitioningWithIdDTO] =
      Seq(withoutCheckpoint, historical, atFrom, atTo, beforeFrom).sortBy(_.id)
    val timeMatches: Seq[PartitioningWithIdDTO] = Seq(historical, atFrom, otherKind).sortBy(_.id)
    val combinedMatches: Seq[PartitioningWithIdDTO] = Seq(historical, atFrom).sortBy(_.id)
  }

  private def createPartitioning(
    name: String,
    kind: String = "main",
    reverseKeys: Boolean = false
  ): ZIO[CreatePartitioning, Throwable, PartitioningWithIdDTO] = {
    val original = Seq(
      PartitionDTO("source", source),
      PartitionDTO("kind", kind),
      PartitionDTO("empty", ""),
      PartitionDTO("case", name)
    )
    val partitioning = if (reverseKeys) original.reverse else original
    val author = s"Search integration author: $name"
    ZIO.serviceWithZIO[CreatePartitioning](_(PartitioningSubmitV2DTO(partitioning, None, author)))
      .flatMap(ZIO.fromEither(_))
      .map(row => PartitioningWithIdDTO(row.data, partitioning, author))
  }

  private def writeCheckpoint(
    partitioning: PartitioningWithIdDTO,
    name: String,
    start: ZonedDateTime,
    end: Option[ZonedDateTime] = None
  ): ZIO[WriteCheckpointV2, Throwable, Unit] = {
    val checkpoint = CheckpointV2DTO(
      id = UUID.nameUUIDFromBytes(s"partitioning-search-${partitioning.partitioning}-$name".getBytes(UTF_8)),
      name = name,
      author = "Checkpoint author, not partitioning author",
      processStartTime = start,
      processEndTime = end,
      measurements = Set(
        MeasurementDTO(MeasureDTO("count", Seq("*")), MeasureResultDTO(TypedValue("1", ResultValueType.LongValue)))
      )
    )
    ZIO.serviceWithZIO[WriteCheckpointV2](_(WriteCheckpointArgs(partitioning.id, checkpoint)))
      .flatMap(ZIO.fromEither(_)).unit
  }

  private val createFixture: ZIO[CreatePartitioning with WriteCheckpointV2, Throwable, Fixture] = for {
    withoutCheckpoint <- createPartitioning("without-checkpoints")
    historical <- createPartitioning("historical")
    atFrom <- createPartitioning("at-from", reverseKeys = true)
    atTo <- createPartitioning("at-to")
    otherKind <- createPartitioning("other-kind", kind = "other")
    beforeFrom <- createPartitioning("before-from")
    _ <- writeCheckpoint(historical, "historical-match", from.plusMinutes(10))
    _ <- writeCheckpoint(historical, "second-match", from.plusMinutes(20))
    _ <- writeCheckpoint(historical, "latest-outside-window", to.plusDays(1))
    _ <- writeCheckpoint(atFrom, "inclusive-lower-bound", from.withZoneSameInstant(ZoneOffset.UTC))
    _ <- writeCheckpoint(atTo, "exclusive-upper-bound", to.withZoneSameInstant(ZoneOffset.UTC))
    _ <- writeCheckpoint(otherKind, "matching-time-different-keys", from.plusMinutes(30))
    _ <- writeCheckpoint(beforeFrom, "end-inside-but-start-outside", from.minusMinutes(1), Some(from.plusMinutes(1)))
  } yield Fixture(withoutCheckpoint, historical, atFrom, atTo, otherKind, beforeFrom)

  private def getPage(
    args: SearchPartitioningsArgs
  ): ZIO[SearchPartitionings, Throwable, Seq[SearchPartitioningsResult]] =
    ZIO.serviceWithZIO[SearchPartitionings](_(args))
      .flatMap(ZIO.fromEither(_))
      .map(_.flatMap(_.data))

  private def decodePartitionings(
    rows: Seq[SearchPartitioningsResult]
  ): ZIO[Any, Throwable, Seq[PartitioningWithIdDTO]] =
    ZIO.fromEither(PartitioningResult.resultsToPartitioningWithIdDTOs(rows))

  private def controller(search: SearchPartitionings): PartitioningControllerImpl = {
    val repository = new PartitioningRepositoryImpl(
      mock(classOf[CreatePartitioning]),
      mock(classOf[GetPartitioningMeasures]),
      mock(classOf[CreateOrUpdateAdditionalData]),
      mock(classOf[GetPartitioningAdditionalData]),
      mock(classOf[GetPartitioningById]),
      mock(classOf[GetPartitioningMeasuresById]),
      mock(classOf[GetPartitioning]),
      mock(classOf[GetFlowPartitionings]),
      mock(classOf[GetPartitioningMainFlow]),
      mock(classOf[UpdatePartitioningParent]),
      mock(classOf[GetPartitioningAncestors]),
      search
    )
    new PartitioningControllerImpl(new PartitioningServiceImpl(repository))
  }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SearchPartitioningsIntegrationTests")(
    test("Binds key-only JSONB and decodes authors and original key-array ordering, including checkpoint-free rows") {
      for {
        fixture <- createFixture
        rawRows <- ZIO.serviceWithZIO[SearchPartitionings](_(baseArgs)).flatMap(ZIO.fromEither(_))
        rows = rawRows.flatMap(_.data)
        partitionings <- decodePartitionings(rows)
      } yield assertTrue(
        rawRows.forall { case Row(status, _) => status == FunctionStatus(11, "OK") },
        partitionings == fixture.keyMatches,
        partitionings.contains(fixture.withoutCheckpoint),
        rows.map(_.partitioningJson) ==
          fixture.keyMatches.map(p => PartitioningForDB.fromSeqPartitionDTO(p.partitioning).asJson),
        rows.forall(!_.hasMore)
      )
    },
    test("Activity-only search matches any historical checkpoint, deduplicates IDs and applies instant-aware half-open bounds") {
      for {
        fixture <- createFixture
        rows <- getPage(baseArgs.copy(keys = None, from = Some(from), to = Some(to)))
        partitionings <- decodePartitionings(rows)
      } yield assertTrue(
        partitionings == fixture.timeMatches,
        rows.map(_.id).distinct.size == rows.size,
        rows.forall(!_.hasMore)
      )
    },
    test("Combines keys with activity and accepts explicit empty keys with a time bound") {
      for {
        fixture <- createFixture
        combined <- getPage(baseArgs.copy(from = Some(from), to = Some(to)))
        emptyKeys <- getPage(baseArgs.copy(keys = Some(Map.empty), from = Some(from), to = Some(to)))
        combinedPartitionings <- decodePartitionings(combined)
        emptyKeyPartitionings <- decodePartitionings(emptyKeys)
      } yield assertTrue(
        combinedPartitionings == fixture.combinedMatches,
        emptyKeyPartitionings == fixture.timeMatches
      )
    },
    test("Binds one-sided time bounds as optional timestamptz values") {
      for {
        fixture <- createFixture
        fromOnly <- getPage(baseArgs.copy(from = Some(from)))
        toOnly <- getPage(baseArgs.copy(to = Some(to)))
        fromPartitionings <- decodePartitionings(fromOnly)
        toPartitionings <- decodePartitionings(toOnly)
      } yield assertTrue(
        fromPartitionings == Seq(fixture.historical, fixture.atFrom, fixture.atTo).sortBy(_.id),
        toPartitionings == Seq(fixture.historical, fixture.atFrom, fixture.beforeFrom).sortBy(_.id)
      )
    },
    test("Orders unique partitioning IDs before pagination and decodes hasMore at every page boundary") {
      for {
        fixture <- createFixture
        first <- getPage(baseArgs.copy(limit = 2))
        middle <- getPage(baseArgs.copy(limit = 2, offset = 2L))
        lastFull <- getPage(baseArgs.copy(limit = 2, offset = 3L))
        lastPartial <- getPage(baseArgs.copy(limit = 2, offset = 4L))
        atEnd <- getPage(baseArgs.copy(limit = 2, offset = 5L))
        largeOffset <- getPage(baseArgs.copy(limit = 2, offset = searchOffset))
        noMatches <- getPage(baseArgs.copy(keys = Some(keys + ("case" -> "does-not-exist"))))
        activeFirst <- getPage(baseArgs.copy(limit = 1, from = Some(from), to = Some(to)))
        activeSecond <- getPage(baseArgs.copy(limit = 1, offset = 1L, from = Some(from), to = Some(to)))
        ids = fixture.keyMatches.map(_.id)
      } yield assertTrue(
        first.map(row => (row.id, row.hasMore)) == ids.take(2).map(_ -> true),
        middle.map(row => (row.id, row.hasMore)) == ids.slice(2, 4).map(_ -> true),
        lastFull.map(row => (row.id, row.hasMore)) == ids.takeRight(2).map(_ -> false),
        lastPartial.map(row => (row.id, row.hasMore)) == Seq(ids.last -> false),
        atEnd.isEmpty,
        largeOffset.isEmpty,
        noMatches.isEmpty,
        activeFirst.map(row => (row.id, row.hasMore)) == Seq(fixture.historical.id -> true),
        activeSecond.map(row => (row.id, row.hasMore)) == Seq(fixture.atFrom.id -> false)
      )
    },
    suite("HTTP nanosecond bounds against microsecond storage")(
      Seq(
        ("excludes a checkpoint before a fractional lower bound", from.plusNanos(100), to,
          (fixture: Fixture) => Seq(fixture.historical)),
        ("includes a checkpoint before a fractional upper bound", from, from.plusNanos(100),
          (fixture: Fixture) => Seq(fixture.atFrom)),
        ("handles a lower bound that ceilings into the next second", from.minusNanos(1), from.plusNanos(100),
          (fixture: Fixture) => Seq(fixture.atFrom)),
        ("returns an empty page for a window without a representable timestamp",
          from.plusNanos(100), from.withZoneSameInstant(ZoneOffset.UTC).plusNanos(200),
          (_: Fixture) => Seq.empty[PartitioningWithIdDTO])
      ).map { case (name, lower, upper, expected) =>
        test(name) {
          for {
            fixture <- createFixture
            search <- ZIO.service[SearchPartitionings]
            request = uri"http://localhost/api/v2/partitionings/search"
              .addParam("keys", encodeSearchJson(keys.asJson.noSpaces))
              .addParam("from", lower.toString)
              .addParam("to", upper.toString)
            response <- PartitioningSearchTestSupport.get(request, controller(search))
            page <- ZIO.fromEither(decode[PaginatedResponse[PartitioningWithIdDTO]](response._2))
          } yield assertTrue(
            response._1 == StatusCode.Ok,
            page.data == expected(fixture),
            page.pagination == Pagination(10, 0L, hasMore = false)
          )
        }
      }: _*
    ),
    test("Registered HTTP endpoint through controller, service and repository returns SQL-backed pages and an empty envelope") {
      for {
        fixture <- createFixture
        search <- ZIO.service[SearchPartitionings]
        realController = controller(search)
        uri = uri"http://localhost/api/v2/partitionings/search"
          .addParam("keys", encodeSearchJson(keys.asJson.noSpaces))
          .addParam("from", from.toString)
          .addParam("to", to.toString)
          .addParam("limit", "1")
        first <- PartitioningSearchTestSupport.get(uri, realController)
        second <- PartitioningSearchTestSupport.get(uri.addParam("offset", "1"), realController)
        empty <- PartitioningSearchTestSupport.get(uri.addParam("offset", "2"), realController)
        firstPage <- ZIO.fromEither(decode[PaginatedResponse[PartitioningWithIdDTO]](first._2))
        secondPage <- ZIO.fromEither(decode[PaginatedResponse[PartitioningWithIdDTO]](second._2))
        emptyPage <- ZIO.fromEither(decode[PaginatedResponse[PartitioningWithIdDTO]](empty._2))
      } yield assertTrue(
        first._1 == StatusCode.Ok,
        second._1 == StatusCode.Ok,
        empty._1 == StatusCode.Ok,
        firstPage.data == Seq(fixture.historical),
        firstPage.pagination == Pagination(1, 0L, hasMore = true),
        secondPage.data == Seq(fixture.atFrom),
        secondPage.pagination == Pagination(1, 1L, hasMore = false),
        emptyPage.data.isEmpty,
        emptyPage.pagination == Pagination(1, 2L, hasMore = false)
      )
    }
  ).provide(
    SearchPartitionings.layer,
    CreatePartitioning.layer,
    WriteCheckpointV2.layer,
    PostgresDatabaseProvider.layer,
    TestTransactorProvider.layerWithSingleTransactionRollback
  ) @@ TestAspect.sequential
}

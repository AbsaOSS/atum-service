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

package za.co.absa.atum.server.api.v2.http

import cats.data.NonEmptyList
import doobie.Transactor
import doobie.implicits._
import doobie.util.fragments
import io.circe.parser.decode
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{UriContext, asStringAlways, basicRequest}
import sttp.model.StatusCode
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.{PartitioningParentPatchDTO, PartitioningWithIdDTO}
import za.co.absa.atum.model.envelopes.SuccessResponse.{PaginatedResponse, SingleSuccessResponse}
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.flows.functions.{GetFlowPartitionings => GetFlowPartitioningsFn}
import za.co.absa.atum.server.api.database.runs.functions._
import za.co.absa.atum.server.api.v2.controller.{PartitioningController, PartitioningControllerImpl}
import za.co.absa.atum.server.api.v2.repository.PartitioningRepositoryImpl
import za.co.absa.atum.server.api.v2.service.PartitioningServiceImpl
import zio._
import zio.interop.catz._
import zio.test._

import java.util.UUID

/**
 *  SERVER-06: replays LITERAL v0.10.0 agent request bodies through the real Tapir endpoints (stub interpreter),
 *  the real controller, service, repository and DB functions, against a real Postgres.
 *  Everything below the Tapir stub is real. Only the three endpoints under test are mounted (not the aggregated
 *  server endpoint list, whose environment would also need the v1 controllers and the metrics publisher).
 *  Each test gets its own single-transaction connection (rolled back at the end) shared by the HTTP calls and the
 *  SQL probes, uses unique tags and only scoped SQL counts.
 */
object PartitioningV2HttpReplayIntegrationTests extends ConfigProviderTest {

  private val SeededMeasureName = "seededCount"
  private val Base = "/api/v2/partitionings"

  private case class Resp(code: StatusCode, location: Option[String], body: String)

  private val postServerEndpoint =
    Endpoints.postPartitioningEndpoint.zServerLogic(PartitioningController.postPartitioning)

  private val patchServerEndpoint =
    Endpoints.patchPartitioningParentEndpoint.zServerLogic({ case (id: Long, dto: PartitioningParentPatchDTO) =>
      PartitioningController.patchPartitioningParent(id, dto)
    })

  private val ancestorsServerEndpoint =
    Endpoints.getPartitioningAncestorsEndpoint.zServerLogic({ case (id: Long, limit: Int, offset: Long) =>
      PartitioningController.getPartitioningAncestors(id, limit, offset)
    })

  private val backendStub =
    TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[PartitioningController]))
      .whenServerEndpointRunLogic(postServerEndpoint)
      .whenServerEndpointRunLogic(patchServerEndpoint)
      .whenServerEndpointRunLogic(ancestorsServerEndpoint)
      .backend()

  // ---------------------------------------------------------------------------------------------------------------
  // Literal v0.10.0 bodies (shapes copied from SerializationUtilsUnitTests; only the key VALUE is made unique)
  // ---------------------------------------------------------------------------------------------------------------

  private def uniqueTag(): String = UUID.randomUUID().toString

  private def literalBody(tag: String, author: String, extra: String): String =
    s"""{"partitioning":[{"key":"replayKey","value":"$tag"}],"author":"$author"$extra}"""

  private def noParentsBody(tag: String, author: String = "agent") =
    literalBody(tag, author, ""","parentPartitioningId":null""")

  private def legacyIdBody(tag: String, parent: Long, author: String = "agent") =
    literalBody(tag, author, s""","parentPartitioningId":$parent""")

  private def legacyStringIdBody(tag: String, parent: Long, author: String = "agent") =
    literalBody(tag, author, s""","parentPartitioningId":"$parent"""")

  private def pluralBody(tag: String, parents: Seq[Long], author: String = "agent") =
    literalBody(tag, author, s""","parentPartitioningIds":[${parents.mkString(",")}]""")

  // ---------------------------------------------------------------------------------------------------------------
  // HTTP helpers
  // ---------------------------------------------------------------------------------------------------------------

  private def post(json: String) =
    basicRequest
      .post(uri"https://test.com/api/v2/partitionings")
      .contentType("application/json")
      .body(json)
      .response(asStringAlways)
      .send(backendStub)
      .map(r => Resp(r.code, r.header("Location"), r.body))

  private def patch(childId: Long, json: String) =
    basicRequest
      .patch(uri"https://test.com/api/v2/partitionings/$childId/ancestors")
      .contentType("application/json")
      .body(json)
      .response(asStringAlways)
      .send(backendStub)
      .map(r => Resp(r.code, r.header("Location"), r.body))

  private def patchBody(parent: Long, author: String = "agent") =
    s"""{"parentPartitioningId":$parent,"author":"$author","copyMeasurements":false,"copyAdditionalData":false}"""

  /** Raw response of the real GET ancestors endpoint. */
  private def getAncestors(id: Long) =
    basicRequest
      .get(uri"https://test.com/api/v2/partitionings/$id/ancestors?limit=100&offset=0")
      .response(asStringAlways)
      .send(backendStub)
      .map(r => Resp(r.code, r.header("Location"), r.body))

  /** Ancestor ids through the real GET endpoint (an empty list for a partitioning without ancestors). */
  private def ancestors(id: Long) =
    getAncestors(id).flatMap { r =>
      ZIO
        .fromEither(decode[PaginatedResponse[PartitioningWithIdDTO]](r.body))
        .mapError(e => new RuntimeException(s"ancestors ${r.code}: ${r.body} ($e)"))
        .map(_.data.map(_.id))
    }

  private def parsed(resp: Resp) =
    ZIO
      .fromEither(decode[SingleSuccessResponse[PartitioningWithIdDTO]](resp.body))
      .mapError(e => new RuntimeException(s"unexpected body ${resp.code}: ${resp.body} ($e)"))
      .map(_.data)

  private def location(id: Long) = Some(s"$Base/$id")

  /** Creates a brand new parentless partitioning over HTTP and returns its id. */
  private def createNew(tag: String) =
    for {
      resp <- post(noParentsBody(tag))
      dto <- parsed(resp)
    } yield dto.id

  // ---------------------------------------------------------------------------------------------------------------
  // SQL probes on the same shared transaction (scoped by unique tag / explicit ids only)
  // ---------------------------------------------------------------------------------------------------------------

  private def countPartitioningsByTag(tag: String) =
    ZIO.serviceWithZIO[Transactor[Task]] { xa =>
      val pattern = s"%$tag%"
      sql"SELECT count(*) FROM runs.partitionings WHERE partitioning::text LIKE $pattern"
        .query[Long]
        .unique
        .transact(xa)
    }

  private def countFlowLinks(ids: Seq[Long]) =
    ZIO.serviceWithZIO[Transactor[Task]] { xa =>
      (fr"SELECT count(*) FROM flows.partitioning_to_flow WHERE" ++
        fragments.in(fr"fk_partitioning", NonEmptyList.fromListUnsafe(ids.toList)))
        .query[Long]
        .unique
        .transact(xa)
    }

  private def seedMeasureDefinition(partitioningId: Long) =
    ZIO.serviceWithZIO[Transactor[Task]] { xa =>
      sql"""INSERT INTO runs.measure_definitions
              (fk_partitioning, measure_name, measured_columns, created_by, created_at)
            VALUES ($partitioningId, $SeededMeasureName, ARRAY['col1'], 'seed', now())""".update.run.transact(xa)
    }

  private def measuresOf(partitioningId: Long) =
    ZIO.serviceWithZIO[GetPartitioningMeasuresById](_(partitioningId)).map { result =>
      result.toOption.get.flatMap(_.data.measureName)
    }

  // ---------------------------------------------------------------------------------------------------------------

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {

    suite("PartitioningV2HttpReplay")(
      test("R1: legacy parentPartitioningId:null -> 201 + Location; same POST again -> 200, same id and Location") {
        val tag = uniqueTag()
        for {
          first <- post(noParentsBody(tag))
          firstDto <- parsed(first)
          second <- post(noParentsBody(tag))
          secondDto <- parsed(second)
        } yield assertTrue(
          first.code == StatusCode.Created,
          first.location == location(firstDto.id),
          second.code == StatusCode.Ok,
          second.code != StatusCode.Conflict,
          secondDto == firstDto,
          second.location == first.location
        )
      },
      test("R1b: v0.10.0 body without any parent key -> 201, then 200") {
        val tag = uniqueTag()
        val body = literalBody(tag, "agent", "")
        for {
          first <- post(body)
          firstDto <- parsed(first)
          second <- post(body)
          secondDto <- parsed(second)
        } yield assertTrue(
          first.code == StatusCode.Created,
          second.code == StatusCode.Ok,
          secondDto.id == firstDto.id,
          second.location == location(firstDto.id)
        )
      },
      test("R2: repeat by a DIFFERENT author -> 200 and the STORED (first) author in the body") {
        val tag = uniqueTag()
        for {
          first <- post(noParentsBody(tag, author = "firstAuthor"))
          firstDto <- parsed(first)
          second <- post(noParentsBody(tag, author = "otherAuthor"))
          secondDto <- parsed(second)
        } yield assertTrue(
          first.code == StatusCode.Created,
          firstDto.author == "firstAuthor",
          second.code == StatusCode.Ok,
          secondDto.id == firstDto.id,
          secondDto.author == "firstAuthor"
        )
      },
      test("R3: legacy numeric and numeric-string parentPartitioningId -> 201, ancestors == [parent]") {
        for {
          parent <- createNew(uniqueTag())
          numeric <- post(legacyIdBody(uniqueTag(), parent))
          numericDto <- parsed(numeric)
          numericAncestors <- ancestors(numericDto.id)
          string <- post(legacyStringIdBody(uniqueTag(), parent))
          stringDto <- parsed(string)
          stringAncestors <- ancestors(stringDto.id)
        } yield assertTrue(
          numeric.code == StatusCode.Created,
          numeric.location == location(numericDto.id),
          numericAncestors == Seq(parent),
          string.code == StatusCode.Created,
          string.location == location(stringDto.id),
          stringAncestors == Seq(parent)
        )
      },
      test("R4: existing partitioning + exactly one NEW parent -> 200 and the parent shows in ancestors (DB-02)") {
        val tag = uniqueTag()
        for {
          parent <- createNew(uniqueTag())
          first <- post(noParentsBody(tag, author = "firstAuthor"))
          firstDto <- parsed(first)
          before <- ancestors(firstDto.id)
          linked <- post(legacyIdBody(tag, parent, author = "otherAuthor"))
          linkedDto <- parsed(linked)
          after <- ancestors(firstDto.id)
          again <- post(legacyIdBody(tag, parent))
        } yield assertTrue(
          first.code == StatusCode.Created,
          before.isEmpty,
          linked.code == StatusCode.Ok,
          linkedDto.id == firstDto.id,
          linkedDto.author == "firstAuthor",
          linked.location == location(firstDto.id),
          after == Seq(parent),
          again.code == StatusCode.Ok
        )
      },
      test("R5: plural parentPartitioningIds [a,b] -> 201, ancestors {a,b}; both keys -> union") {
        for {
          p1 <- createNew(uniqueTag())
          p2 <- createNew(uniqueTag())
          plural <- post(pluralBody(uniqueTag(), Seq(p1, p2)))
          pluralDto <- parsed(plural)
          pluralAncestors <- ancestors(pluralDto.id)
          both <- post(
            literalBody(uniqueTag(), "agent", s""","parentPartitioningId":$p1,"parentPartitioningIds":[$p1,$p2]""")
          )
          bothDto <- parsed(both)
          bothAncestors <- ancestors(bothDto.id)
        } yield assertTrue(
          plural.code == StatusCode.Created,
          pluralAncestors.size == 2,
          pluralAncestors.toSet == Set(p1, p2),
          both.code == StatusCode.Created,
          bothAncestors.size == 2,
          bothAncestors.toSet == Set(p1, p2)
        )
      },
      test("R6: measure definitions are NOT inherited without the flag or with null; inherited with true") {
        for {
          parent <- createNew(uniqueTag())
          _ <- seedMeasureDefinition(parent)
          noFlag <- post(legacyIdBody(uniqueTag(), parent))
          noFlagDto <- parsed(noFlag)
          nullFlag <- post(
            literalBody(uniqueTag(), "agent", s""","parentPartitioningId":$parent,"copyMeasureDefinitions":null""")
          )
          nullFlagDto <- parsed(nullFlag)
          falseFlag <- post(
            literalBody(uniqueTag(), "agent", s""","parentPartitioningId":$parent,"copyMeasureDefinitions":false""")
          )
          falseFlagDto <- parsed(falseFlag)
          trueFlag <- post(
            literalBody(uniqueTag(), "agent", s""","parentPartitioningId":$parent,"copyMeasureDefinitions":true""")
          )
          trueFlagDto <- parsed(trueFlag)
          noFlagMeasures <- measuresOf(noFlagDto.id)
          nullFlagMeasures <- measuresOf(nullFlagDto.id)
          falseFlagMeasures <- measuresOf(falseFlagDto.id)
          trueFlagMeasures <- measuresOf(trueFlagDto.id)
        } yield assertTrue(
          noFlag.code == StatusCode.Created,
          nullFlag.code == StatusCode.Created,
          falseFlag.code == StatusCode.Created,
          trueFlag.code == StatusCode.Created,
          noFlagMeasures.isEmpty,
          nullFlagMeasures.isEmpty,
          falseFlagMeasures.isEmpty,
          trueFlagMeasures == Seq(SeededMeasureName)
        )
      },
      test("R7: PATCH {parentPartitioningId, author, copy*:false} -> 204; repeated -> 204 (idempotent)") {
        for {
          parent <- createNew(uniqueTag())
          child <- createNew(uniqueTag())
          first <- patch(child, patchBody(parent))
          second <- patch(child, patchBody(parent))
          after <- ancestors(child)
        } yield assertTrue(
          first.code == StatusCode.NoContent,
          second.code == StatusCode.NoContent,
          after == Seq(parent)
        )
      },
      test("R8: PATCH cycle -> 409 and nothing is written (ancestors and scoped link counts unchanged)") {
        for {
          c <- createNew(uniqueTag())
          pResp <- post(legacyIdBody(uniqueTag(), c)) // P is a child of C
          pDto <- parsed(pResp)
          p = pDto.id
          countBefore <- countFlowLinks(Seq(c, p))
          ancestorsOfCBefore <- ancestors(c)
          ancestorsOfPBefore <- ancestors(p)
          cycle <- patch(c, patchBody(p)) // would make C a child of its own descendant
          countAfter <- countFlowLinks(Seq(c, p))
          ancestorsOfCAfter <- ancestors(c)
          ancestorsOfPAfter <- ancestors(p)
        } yield assertTrue(
          pResp.code == StatusCode.Created,
          ancestorsOfPBefore == Seq(c),
          cycle.code == StatusCode.Conflict,
          countAfter == countBefore,
          ancestorsOfCBefore.isEmpty,
          ancestorsOfCAfter.isEmpty,
          ancestorsOfPAfter == Seq(c)
        )
      },
      test("R9: POST with an unknown parent -> 404 and nothing is written") {
        val tag = uniqueTag()
        for {
          resp <- post(legacyIdBody(tag, Long.MaxValue))
          written <- countPartitioningsByTag(tag)
          plural <- post(pluralBody(tag, Seq(Long.MaxValue)))
          writtenAfterPlural <- countPartitioningsByTag(tag)
        } yield assertTrue(
          resp.code == StatusCode.NotFound,
          written == 0L,
          plural.code == StatusCode.NotFound,
          writtenAfterPlural == 0L
        )
      },
      test("R10: POST cycle -> 409; 'already exists' is never 409") {
        val tagC = uniqueTag()
        for {
          cResp <- post(noParentsBody(tagC))
          cDto <- parsed(cResp)
          pResp <- post(legacyIdBody(uniqueTag(), cDto.id)) // P is a child of C
          pDto <- parsed(pResp)
          countBefore <- countFlowLinks(Seq(cDto.id, pDto.id))
          cycle <- post(legacyIdBody(tagC, pDto.id)) // C would become a child of its own descendant
          countAfter <- countFlowLinks(Seq(cDto.id, pDto.id))
          ancestorsOfC <- ancestors(cDto.id)
          repeat <- post(noParentsBody(tagC))
        } yield assertTrue(
          cResp.code == StatusCode.Created,
          pResp.code == StatusCode.Created,
          cycle.code == StatusCode.Conflict,
          countAfter == countBefore,
          ancestorsOfC.isEmpty,
          repeat.code == StatusCode.Ok
        )
      },
      test("R11: GET ancestors -> 200 + empty list without ancestors, 404 if the partitioning is unknown") {
        for {
          id <- createNew(uniqueTag())
          none <- getAncestors(id)
          noneBody <- ZIO
            .fromEither(decode[PaginatedResponse[PartitioningWithIdDTO]](none.body))
            .mapError(e => new RuntimeException(s"unexpected body ${none.code}: ${none.body} ($e)"))
          unknown <- getAncestors(Long.MaxValue)
        } yield assertTrue(
          none.code == StatusCode.Ok,
          noneBody.data.isEmpty,
          unknown.code == StatusCode.NotFound
        )
      }
    ).provide(
      PartitioningControllerImpl.layer,
      PartitioningServiceImpl.layer,
      PartitioningRepositoryImpl.layer,
      CreatePartitioningWithParents.layer,
      GetPartitioningMeasures.layer,
      CreateOrUpdateAdditionalData.layer,
      GetPartitioningAdditionalData.layer,
      GetPartitioningById.layer,
      GetPartitioningMeasuresById.layer,
      GetPartitioning.layer,
      GetFlowPartitioningsFn.layer,
      GetPartitioningMainFlow.layer,
      UpdatePartitioningParent.layer,
      GetPartitioningAncestors.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithSingleTransactionRollback
    ) @@ TestAspect.sequential

  }

}

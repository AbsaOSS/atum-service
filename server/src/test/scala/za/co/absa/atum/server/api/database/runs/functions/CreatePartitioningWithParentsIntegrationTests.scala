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

import doobie.Transactor
import doobie.implicits._
import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningSubmitV2DTO}
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningAncestors.GetPartitioningAncestorsArgs
import za.co.absa.atum.server.api.{TestData, TestTransactorProvider}
import za.co.absa.atum.server.model.database.PartitioningForDB
import za.co.absa.db.fadb.exceptions.{DataConflictException, DataNotFoundException}
import zio._
import zio.interop.catz._
import zio.test._

object CreatePartitioningWithParentsIntegrationTests extends ConfigProviderTest with TestData {

  private val SeededMeasureName = "seededCount"

  private def uniquePartitioning(tag: String) =
    Seq(PartitionDTO("withParentsKey", s"$tag-${java.util.UUID.randomUUID()}"))

  private def create(
    partitioning: Seq[PartitionDTO],
    parents: Seq[Long],
    author: String = "author",
    copy: Boolean = false
  ) =
    ZIO.serviceWithZIO[CreatePartitioningWithParents](
      _(PartitioningSubmitV2DTO(partitioning, parents, author, copyMeasureDefinitions = copy))
    )

  /** Creates a brand new, parentless partitioning and returns its id. */
  private def createNew(tag: String) =
    create(uniquePartitioning(tag), Seq.empty[Long]).map(_.toOption.get.data.id)

  /** Ids of the ancestors of the given partitioning, read through the real `GetPartitioningAncestors` wrapper. */
  private def ancestorIds(partitioningId: Long) =
    ZIO.serviceWithZIO[GetPartitioningAncestors](_(GetPartitioningAncestorsArgs(partitioningId, 100, 0L))).map {
      result => result.toOption.get.flatMap(_.data).map(_.id)
    }

  private def measureNames(partitioningId: Long) =
    ZIO.serviceWithZIO[GetPartitioningMeasuresById](_(partitioningId)).map { result =>
      result.toOption.get.flatMap(_.data.measureName)
    }

  private def getPartitioning(partitioning: Seq[PartitionDTO]) =
    ZIO.serviceWithZIO[GetPartitioning](_(PartitioningForDB.fromSeqPartitionDTO(partitioning)))

  /** Seeds one measure definition directly on the shared single-transaction connection (rolled back at the end). */
  private def seedMeasure(partitioningId: Long) =
    ZIO.serviceWithZIO[Transactor[Task]] { xa =>
      sql"""INSERT INTO runs.measure_definitions
              (fk_partitioning, measure_name, measured_columns, created_by, created_at)
            VALUES ($partitioningId, $SeededMeasureName, ARRAY['col1'], 'seed', now())""".update.run.transact(xa)
    }

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {

    suite("CreatePartitioningWithParentsSuite")(
      test("T-a: new partitioning with 0 parents -> status 11, id and requester as created_by") {
        for {
          result <- create(uniquePartitioning("a"), Seq.empty[Long], author = "requesterA")
        } yield assertTrue(
          result.isRight,
          result.toOption.get.functionStatus.statusCode == 11,
          result.toOption.get.data.id > 0L,
          result.toOption.get.data.createdBy == "requesterA"
        )
      },
      test("T-b: new partitioning with 1 parent -> status 12, ancestors == [parent]") {
        for {
          parent <- createNew("bParent")
          child <- create(uniquePartitioning("bChild"), Seq(parent))
          ancestors <- ancestorIds(child.toOption.get.data.id)
        } yield assertTrue(
          child.isRight,
          child.toOption.get.functionStatus.statusCode == 12,
          ancestors == Seq(parent)
        )
      },
      test("T-c: same call again -> status 14, same id, STORED created_by even with a different requester") {
        val key = uniquePartitioning("c")
        for {
          first <- create(key, Seq.empty[Long], author = "firstAuthor")
          second <- create(key, Seq.empty[Long], author = "otherRequester")
        } yield assertTrue(
          first.isRight,
          second.isRight,
          first.toOption.get.functionStatus.statusCode == 11,
          second.toOption.get.functionStatus.statusCode == 14,
          second.toOption.get.data.id == first.toOption.get.data.id,
          second.toOption.get.data.createdBy == "firstAuthor"
        )
      },
      test("T-d: existing child + NEW parent -> status 13, same id, parent visible in ancestors (DB-02)") {
        val childKey = uniquePartitioning("dChild")
        for {
          child <- create(childKey, Seq.empty[Long])
          parent <- createNew("dParent")
          childId = child.toOption.get.data.id
          before <- ancestorIds(childId)
          linked <- create(childKey, Seq(parent), author = "otherRequester")
          after <- ancestorIds(childId)
        } yield assertTrue(
          linked.isRight,
          linked.toOption.get.functionStatus.statusCode == 13,
          linked.toOption.get.data.id == childId,
          linked.toOption.get.data.createdBy == "author",
          before.isEmpty,
          after == Seq(parent)
        )
      },
      test("T-e: existing child already linked to the parent -> status 14, ancestors unchanged") {
        val childKey = uniquePartitioning("eChild")
        for {
          parent <- createNew("eParent")
          child <- create(childKey, Seq(parent))
          childId = child.toOption.get.data.id
          again <- create(childKey, Seq(parent))
          after <- ancestorIds(childId)
        } yield assertTrue(
          child.toOption.get.functionStatus.statusCode == 12,
          again.isRight,
          again.toOption.get.functionStatus.statusCode == 14,
          again.toOption.get.data.id == childId,
          after == Seq(parent)
        )
      },
      test("T-f: unknown parent -> Left status 41 and nothing is written") {
        val key = uniquePartitioning("fChild")
        for {
          result <- create(key, Seq(Long.MaxValue))
          stored <- getPartitioning(key)
        } yield assertTrue(
          result.left.exists {
            case DataNotFoundException(status) => status.statusCode == 41
            case _                             => false
          },
          stored.left.exists {
            case DataNotFoundException(status) => status.statusCode == 41
            case _                             => false
          }
        )
      },
      test("T-f2: one known + one unknown parent -> Left status 41 and nothing is written") {
        val key = uniquePartitioning("f2Child")
        for {
          parent <- createNew("f2Parent")
          result <- create(key, Seq(parent, Long.MaxValue))
          stored <- getPartitioning(key)
        } yield assertTrue(
          result.left.exists {
            case DataNotFoundException(status) => status.statusCode == 41
            case _                             => false
          },
          stored.isLeft
        )
      },
      test("T-g: cycle -> Left status 32 and ancestors unchanged") {
        val keyC = uniquePartitioning("gC")
        for {
          c <- create(keyC, Seq.empty[Long])
          cId = c.toOption.get.data.id
          p <- create(uniquePartitioning("gP"), Seq(cId)) // P is a child of C
          pId = p.toOption.get.data.id
          result <- create(keyC, Seq(pId)) // would make C a child of its own descendant
          ancestorsOfC <- ancestorIds(cId)
          ancestorsOfP <- ancestorIds(pId)
        } yield assertTrue(
          p.toOption.get.functionStatus.statusCode == 12,
          result.left.exists {
            case DataConflictException(status) => status.statusCode == 32
            case _                             => false
          },
          ancestorsOfC.isEmpty,
          ancestorsOfP == Seq(cId)
        )
      },
      test("T-g2: self-link of an existing partitioning -> Left status 32, ancestors unchanged") {
        val key = uniquePartitioning("selfX")
        for {
          x <- create(key, Seq.empty[Long])
          xId = x.toOption.get.data.id
          result <- create(key, Seq(xId))
          ancestors <- ancestorIds(xId)
        } yield assertTrue(
          result.left.exists {
            case DataConflictException(status) => status.statusCode == 32
            case _                             => false
          },
          ancestors.isEmpty
        )
      },
      test("T-h: duplicate parent ids behave like a single parent") {
        for {
          parent <- createNew("hParent")
          child <- create(uniquePartitioning("hChild"), Seq(parent, parent))
          ancestors <- ancestorIds(child.toOption.get.data.id)
        } yield assertTrue(
          child.isRight,
          child.toOption.get.functionStatus.statusCode == 12,
          ancestors == Seq(parent)
        )
      },
      test("T-i: two parents -> status 12, ancestors == both parents") {
        for {
          p1 <- createNew("iP1")
          p2 <- createNew("iP2")
          child <- create(uniquePartitioning("iChild"), Seq(p1, p2))
          ancestors <- ancestorIds(child.toOption.get.data.id)
        } yield assertTrue(
          child.isRight,
          child.toOption.get.functionStatus.statusCode == 12,
          ancestors.size == 2,
          ancestors.toSet == Set(p1, p2)
        )
      },
      test("T-j1: flag true -> NEW child receives the parent's measure definition") {
        for {
          parent <- createNew("mdTrueParent")
          _ <- seedMeasure(parent)
          child <- create(uniquePartitioning("mdTrueChild"), Seq(parent), copy = true)
          measures <- measureNames(child.toOption.get.data.id)
        } yield assertTrue(
          child.isRight,
          child.toOption.get.functionStatus.statusCode == 12,
          measures == Seq(SeededMeasureName)
        )
      },
      test("T-j2: flag false -> NEW child gets NO measure definitions") {
        for {
          parent <- createNew("mdFalseParent")
          _ <- seedMeasure(parent)
          child <- create(uniquePartitioning("mdFalseChild"), Seq(parent), copy = false)
          measures <- measureNames(child.toOption.get.data.id)
        } yield assertTrue(child.isRight, child.toOption.get.functionStatus.statusCode == 12, measures.isEmpty)
      },
      test("T-j3: flag absent (default) -> NEW child gets NO measure definitions") {
        for {
          createWithParents <- ZIO.service[CreatePartitioningWithParents]
          parent <- createNew("mdAbsentParent")
          _ <- seedMeasure(parent)
          child <- createWithParents(
            PartitioningSubmitV2DTO(uniquePartitioning("mdAbsentChild"), Seq(parent), "author")
          )
          measures <- measureNames(child.toOption.get.data.id)
        } yield assertTrue(child.isRight, child.toOption.get.functionStatus.statusCode == 12, measures.isEmpty)
      },
      test("T-j4: EXISTING child + flag true -> nothing is copied") {
        val childKey = uniquePartitioning("mdExistingChild")
        for {
          parent <- createNew("mdExistingParent")
          _ <- seedMeasure(parent)
          existing <- create(childKey, Seq.empty[Long])
          linked <- create(childKey, Seq(parent), copy = true)
          measures <- measureNames(existing.toOption.get.data.id)
        } yield assertTrue(
          existing.isRight,
          linked.isRight,
          linked.toOption.get.functionStatus.statusCode == 13,
          linked.toOption.get.data.id == existing.toOption.get.data.id,
          measures.isEmpty
        )
      }
    ).provide(
      CreatePartitioningWithParents.layer,
      GetPartitioningAncestors.layer,
      GetPartitioningMeasuresById.layer,
      GetPartitioning.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithSingleTransactionRollback
    ) @@ TestAspect.sequential

  }

}

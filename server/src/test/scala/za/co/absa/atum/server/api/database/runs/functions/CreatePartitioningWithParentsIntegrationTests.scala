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

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

import doobie.implicits.toSqlInterpolator
import doobie.postgres.implicits._
import io.circe.syntax.EncoderOps
import za.co.absa.atum.model.dto.PartitioningSubmitV2DTO
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.model.database.PartitioningForDB
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbPut
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio._

class CreatePartitioningWithParents(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieSingleResultFunctionWithStatus[PartitioningSubmitV2DTO, CreatePartitioningWithParents.DBResult, Task](
      partitioningSubmit =>
        Seq(
          fr"${PartitioningForDB.fromSeqPartitionDTO(partitioningSubmit.partitioning).asJson}",
          fr"${partitioningSubmit.author}",
          fr"${partitioningSubmit.parentPartitioningIds.toList}::BIGINT[]",
          fr"${partitioningSubmit.copyMeasureDefinitions}"
        )
    )
    with StandardStatusHandling {

  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq("id_partitioning", "created_by", "was_created")
}

object CreatePartitioningWithParents {
  val layer: URLayer[PostgresDatabaseProvider, CreatePartitioningWithParents] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new CreatePartitioningWithParents()(Runs, dbProvider.dbEngine)
  }

  private case class DBResult(
    id: Long,
    createdBy: String,
    wasCreated: Boolean
  )
}

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
import za.co.absa.atum.model.dto.PartitioningParentsSubmitDTO
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.api.database.runs.functions.LinkPartitioningToParents.LinkPartitioningToParentsArgs
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio._

class LinkPartitioningToParents(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieSingleResultFunctionWithStatus[LinkPartitioningToParentsArgs, Unit, Task](args =>
      Seq(
        fr"${args.partitioningId}",
        fr"${args.submitDTO.parentPartitioningIds.toList}::BIGINT[]",
        fr"${args.submitDTO.author}",
        fr"${args.submitDTO.copyMeasureDefinitions.getOrElse(false)}"
      )
    )
    with StandardStatusHandling

object LinkPartitioningToParents {
  case class LinkPartitioningToParentsArgs(
    partitioningId: Long,
    submitDTO: PartitioningParentsSubmitDTO
  )

  val layer: URLayer[PostgresDatabaseProvider, LinkPartitioningToParents] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new LinkPartitioningToParents()(Runs, dbProvider.dbEngine)
  }
}

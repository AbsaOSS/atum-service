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
import io.circe.syntax._
import za.co.absa.atum.model.dto.PartitioningParentPatchDTO
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.api.database.runs.functions.UpdatePartitioningParent.UpdatePartitioningParentArgs
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio._

class UpdatePartitioningParent(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieSingleResultFunctionWithStatus[UpdatePartitioningParentArgs, Unit, Task](args =>
      Seq(
        fr"${args.partitioningId}",
        fr"${args.partitioningParentPatchDTO.parentPartitioningId}",
        fr"${args.partitioningParentPatchDTO.author}",
        fr"${args.partitioningParentPatchDTO.copyMeasurements}",
        fr"${args.partitioningParentPatchDTO.copyAdditionalData}",
      ),
      Some("update_partitioning_parent")
    )
    with StandardStatusHandling

object UpdatePartitioningParent {
  case class UpdatePartitioningParentArgs(
    partitioningId: Long,
    partitioningParentPatchDTO: PartitioningParentPatchDTO
 )

  val layer: URLayer[PostgresDatabaseProvider, UpdatePartitioningParent] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new UpdatePartitioningParent()(Runs, dbProvider.dbEngine)
  }
}

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
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import zio._
import io.circe.syntax._
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbArrayPut
import doobie.postgres.implicits._
import za.co.absa.atum.model.dto.CheckpointV2DTO
import za.co.absa.atum.server.api.database.runs.functions.WriteCheckpointV2.WriteCheckpointArgs

class WriteCheckpointV2(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieSingleResultFunctionWithStatus[WriteCheckpointArgs, Unit, Task](args =>
      Seq(
        fr"${args.partitioningId}",
        fr"${args.checkpointV2DTO.id}",
        fr"${args.checkpointV2DTO.name}",
        fr"${args.checkpointV2DTO.processStartTime}",
        fr"${args.checkpointV2DTO.processEndTime}",
        fr"${args.checkpointV2DTO.measurements.toList.map(_.asJson)}",
        fr"${args.checkpointV2DTO.author}",
        fr"${args.checkpointV2DTO.measuredByAtumAgent}"
      ),
      Some("write_checkpoint")
    )
    with StandardStatusHandling

object WriteCheckpointV2 {
  case class WriteCheckpointArgs(partitioningId: Long, checkpointV2DTO: CheckpointV2DTO)

  val layer: URLayer[PostgresDatabaseProvider, WriteCheckpointV2] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new WriteCheckpointV2()(Runs, dbProvider.dbEngine)
  }
}

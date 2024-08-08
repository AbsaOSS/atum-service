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
import za.co.absa.atum.server.model.WriteCheckpointV2Args
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

class WriteCheckpointV2(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieSingleResultFunctionWithStatus[WriteCheckpointV2Args, Unit, Task](args =>
      Seq(
        fr"${args.partitioningId}",
        fr"${args.checkpointV2DTO.id}",
        fr"${args.checkpointV2DTO.name}",
        fr"${args.checkpointV2DTO.processStartTime}",
        fr"${args.checkpointV2DTO.processEndTime}",
        fr"${args.checkpointV2DTO.measurements.toList.map(_.asJson)}",
        fr"${args.checkpointV2DTO.measuredByAtumAgent}",
        fr"${args.checkpointV2DTO.author}"
      )
    )
    with StandardStatusHandling

object WriteCheckpointV2 {
  val layer: URLayer[PostgresDatabaseProvider, WriteCheckpointV2] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new WriteCheckpointV2()(Runs, dbProvider.dbEngine)
  }
}

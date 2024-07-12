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

import doobie.Fragment
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.model.PartitioningForDB
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.{DoobieEngine, StatusWithData}
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import zio._
import zio.interop.catz._
import io.circe.syntax._
import za.co.absa.atum.model.dto.MeasureResultDTO._
import za.co.absa.atum.server.api.database.DoobieImplicits.Sequence.get
import za.co.absa.atum.server.api.database.DoobieImplicits.Jsonb.jsonbArrayPut
import doobie.postgres.circe.jsonb.implicits.jsonbGet
import doobie.postgres.circe.jsonb.implicits.jsonbPut
import doobie.postgres.implicits._

class WriteCheckpoint(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieSingleResultFunctionWithStatus[CheckpointDTO, Unit, Task](values => Seq(fr"$values"))
    with StandardStatusHandling {

  override def sql(values: CheckpointDTO)(implicit read: Read[StatusWithData[Unit]]): Fragment = {
    val partitioning = PartitioningForDB.fromSeqPartitionDTO(values.partitioning)
    val partitioningNormalized = partitioning.asJson

    // List[String] containing json data has to be properly escaped
    // It would be safer to use Json data type and derive Put instance
    val measurementsNormalized = {
      values.measurements.toList.map(_.asJson)
    }

    sql"""SELECT ${Fragment.const(selectEntry)} FROM ${Fragment.const(functionName)}(
                  $partitioningNormalized,
                  ${values.id},
                  ${values.name},
                  ${values.processStartTime},
                  ${values.processEndTime},
                  $measurementsNormalized,
                  ${values.measuredByAtumAgent},
                  ${values.author}
                ) ${Fragment.const(alias)};"""
  }
}

object WriteCheckpoint {
  val layer: URLayer[PostgresDatabaseProvider, WriteCheckpoint] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new WriteCheckpoint()(Runs, dbProvider.dbEngine)
  }
}

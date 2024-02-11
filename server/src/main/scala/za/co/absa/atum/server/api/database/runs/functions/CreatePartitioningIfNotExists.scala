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
import play.api.libs.json.Json
import za.co.absa.atum.model.dto.PartitioningSubmitDTO
import za.co.absa.atum.model.utils.SerializationUtils
import za.co.absa.atum.server.model.PartitioningForDB
import za.co.absa.fadb.DBSchema
import za.co.absa.fadb.doobie.{DoobieEngine, StatusWithData}
import za.co.absa.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.fadb.status.handling.implementations.StandardStatusHandling
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import zio._
import zio.interop.catz._

class CreatePartitioningIfNotExists(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieSingleResultFunctionWithStatus[PartitioningSubmitDTO, Unit, Task]
    with StandardStatusHandling {

  override def sql(values: PartitioningSubmitDTO)(implicit read: Read[StatusWithData[Unit]]): Fragment = {
    val partitioning = PartitioningForDB.fromSeqPartitionDTO(values.partitioning)
    val partitioningJsonString = Json.toJson(partitioning).toString()

    val parentPartitioningJsonString = values.parentPartitioning.map { parentPartitioning =>
      val parentPartitioningForDB = PartitioningForDB.fromSeqPartitionDTO(parentPartitioning)
      Json.toJson(parentPartitioningForDB).toString()
    }

    sql"""SELECT ${Fragment.const(selectEntry)} FROM ${Fragment.const(functionName)}(
                  ${
                    import za.co.absa.atum.server.api.database.DoobieImplicits.Jsonb.jsonbPutUsingString
                    partitioningJsonString
                  },
                  ${values.authorIfNew},
                  ${
                    import za.co.absa.atum.server.api.database.DoobieImplicits.Jsonb.jsonbPutUsingString
                    parentPartitioningJsonString
                  }
                ) ${Fragment.const(alias)};"""
  }
}

object CreatePartitioningIfNotExists {
  val layer: URLayer[PostgresDatabaseProvider, CreatePartitioningIfNotExists] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new CreatePartitioningIfNotExists()(Runs, dbProvider.dbEngine)
  }
}

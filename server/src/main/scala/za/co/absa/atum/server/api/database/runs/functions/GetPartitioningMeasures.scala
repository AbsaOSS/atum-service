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
import za.co.absa.atum.model.dto.{MeasureDTO, PartitioningDTO}
import za.co.absa.atum.server.model.PartitioningForDB
import za.co.absa.fadb.DBSchema
import za.co.absa.fadb.doobie.DoobieEngine
import za.co.absa.fadb.doobie.DoobieFunction.DoobieMultipleResultFunction
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import zio._
import zio.interop.catz._
import io.circe.syntax._

import za.co.absa.atum.server.api.database.DoobieImplicits.Sequence.get

class GetPartitioningMeasures (implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
  extends DoobieMultipleResultFunction[PartitioningDTO, MeasureDTO, Task]
  {
    import za.co.absa.atum.server.api.database.DoobieImplicits.Jsonb.jsonbPutUsingString

    override val fieldsToSelect: Seq[String] = Seq("measure_name", "measured_columns")

    override def sql(values: PartitioningDTO)(implicit read: Read[MeasureDTO]): Fragment = {
    val partitioning = PartitioningForDB.fromSeqPartitionDTO(values)
    val partitioningJsonString = partitioning.asJson.noSpaces

    sql"""SELECT ${Fragment.const(selectEntry)} FROM ${Fragment.const(functionName)}(
                  ${
                    partitioningJsonString
                  }
                ) ${Fragment.const(alias)};"""
  }

}

object GetPartitioningMeasures {
  val layer: URLayer[PostgresDatabaseProvider, GetPartitioningMeasures] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetPartitioningMeasures()(Runs, dbProvider.dbEngine)
  }
}

